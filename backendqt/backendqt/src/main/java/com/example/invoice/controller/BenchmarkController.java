package com.example.invoice.controller;

import com.example.invoice.service.BenchmarkAdapter;
import com.example.invoice.config.AiProperties;
import com.example.invoice.ai.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.nio.file.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/benchmark")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name="benchmark.enabled", havingValue="true")
public class BenchmarkController {
    private final BenchmarkAdapter adapter; private final ObjectMapper json; private final AiProperties properties;
    private final OllamaAiProvider ollama;
    private final okhttp3.OkHttpClient http;
    private final java.util.concurrent.Semaphore gate=new java.util.concurrent.Semaphore(1);
    private final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor(r->{
        var thread=new Thread(r,"benchmark-file-only");thread.setDaemon(true);return thread;
    });
    @org.springframework.beans.factory.annotation.Value("${benchmark.timeout-seconds:900}")
    private long timeoutSeconds=900;
    @jakarta.annotation.PreDestroy public void close() { worker.shutdownNow(); }
    public BenchmarkController(BenchmarkAdapter adapter,ObjectMapper json,AiProperties properties,OllamaAiProvider ollama,okhttp3.OkHttpClient http) {
        this.adapter=adapter;this.json=json;this.properties=properties;this.ollama=ollama;this.http=http;
    }
    @GetMapping("/health") public Map<String,Object> health() {
        var result=new LinkedHashMap<String,Object>();
        result.put("apiVersion","1.0");result.put("provider",properties.getProvider());
        result.put("model",properties.getOllama().getModel());result.put("ocrEngine","Tess4J/Tesseract + adaptive OCR / PDFBox text layer");
        result.put("timeoutSeconds",properties.getRequestTimeout().toSeconds());
        result.put("numContext",properties.getOllama().getNumContext());result.put("numPredict",properties.getOllama().getNumPredict());
        result.put("promptVersion",classHash(AiDocumentPromptFactory.class));result.put("parserVersion",classHash(AiDocumentResultParser.class));
        result.put("pipelineVersion",classHash(com.example.invoice.service.DocumentAiProcessingService.class));
        result.put("validatorVersion",classHash(AiDocumentResultValidator.class));
        result.put("normalizerVersion",classHash(com.example.invoice.service.OcrTextNormalizer.class));
        result.put("layoutVersion",classHash(com.example.invoice.service.OcrLayoutReconstructor.class));
        result.put("pdfVersion",classHash(com.example.invoice.service.PdfImageConversionService.class));
        result.put("tess4jAdapterVersion",classHash(com.example.invoice.service.Tess4jOcrService.class));
        result.put("ocrVersion",classHash(com.example.invoice.service.AdaptiveOcrService.class));
        result.put("engineVersion",Objects.toString(net.sourceforge.tess4j.Tesseract.class.getPackage().getImplementationVersion(),"UNKNOWN"));
        try { result.put("configurationHash",BenchmarkAdapter.hash(json.writeValueAsString(properties))); }
        catch(Exception e) { throw new IllegalStateException(e); }
        result.put("modelDigest",null);
        result.put("ocrConfigured",properties.getOcr().isEnabled() && Arrays.stream(properties.getOcr().getLanguage().split("\\+"))
            .allMatch(lang->Files.isRegularFile(Path.of(properties.getOcr().getDataPath()).resolve(lang+".traineddata"))));
        try {
            boolean ready="ollama".equalsIgnoreCase(properties.getProvider()) && ollama.checkConnectivity()!=null;
            result.put("aiReady",ready);
            if(ready)try(var response=http.newBuilder().callTimeout(java.time.Duration.ofSeconds(10)).build()
                    .newCall(new okhttp3.Request.Builder().url(properties.getOllama().getBaseUrl().replaceAll("/+$","")+"/api/tags").get().build()).execute()) {
                if(response.isSuccessful()&&response.body()!=null)for(var model:json.readTree(response.body().string()).path("models"))
                    if(properties.getOllama().getModel().equals(model.path("name").asText()))result.put("modelDigest",model.path("digest").asText(null));
            }
        }
        catch(Exception e) { result.put("aiReady",false); }
        return result;
    }
    @PostMapping(value="/ocr",consumes="multipart/form-data") public BenchmarkAdapter.Output ocr(
            @RequestParam("file") MultipartFile file,@RequestParam("options") String options) { return run(file,options,false); }
    @PostMapping(value="/ai",consumes="multipart/form-data") public BenchmarkAdapter.Output ai(
            @RequestParam("file") MultipartFile file,@RequestParam("options") String options,
            @RequestParam(value="mode",defaultValue="SYSTEM") String mode) {
        if(!"SYSTEM".equals(mode)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only SYSTEM mode is supported");
        return run(file,options,true);
    }
    private BenchmarkAdapter.Output run(MultipartFile file,String options,boolean ai) {
        if(file.isEmpty()||file.getSize()>properties.getMaxImageSizeBytes()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_FILE_SIZE");
        try {
            byte[] bytes=file.getBytes();
            String mime=bytes.length>=5&&new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-") ? "application/pdf"
                : bytes.length>=8&&bytes[0]==(byte)137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71 ? "image/png"
                : bytes.length>=3&&bytes[0]==(byte)255&&bytes[1]==(byte)216&&bytes[2]==(byte)255 ? "image/jpeg":null;
            if(mime==null) throw new IllegalArgumentException();
            var parsed=json.readValue(options,BenchmarkAdapter.Options.class);
            if(!gate.tryAcquire()) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"BENCHMARK_BUSY");
            var task=worker.submit(()->{
                org.slf4j.MDC.put("benchmark.fileOnly","true");
                try{return adapter.run(bytes,mime,ai,parsed);}
                finally{org.slf4j.MDC.remove("benchmark.fileOnly");gate.release();}
            });
            try { return task.get(Math.max(1,timeoutSeconds),java.util.concurrent.TimeUnit.SECONDS); }
            catch(java.util.concurrent.TimeoutException e) {
                // Native OCR may ignore interruption. Keep the gate held until the task really exits.
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"BENCHMARK_TIMEOUT");
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"BENCHMARK_INTERRUPTED");
            } catch(java.util.concurrent.ExecutionException e) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"BENCHMARK_PROCESSING_FAILED");
            }
        } catch(java.io.IOException|IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_FILE_OR_OPTIONS"); }
    }
    private static String classHash(Class<?> type) {
        try(var stream=type.getResourceAsStream(type.getSimpleName()+".class")) {
            return BenchmarkAdapter.hash(Base64.getEncoder().encodeToString(stream.readAllBytes()));
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
