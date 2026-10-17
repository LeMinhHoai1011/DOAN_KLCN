package com.example.invoice.evaluation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Read-only, opt-in evaluation store. No repositories, business IDs, writes, OCR or model calls. */
@Service
public class EvaluationService {
    private final String directory;
    private final ObjectMapper json;
    public EvaluationService(@Value("${app.evaluation.directory:}") String directory, ObjectMapper json) {
        this.directory=directory;
        this.json=json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }
    public record RunReport(String runId,String datasetId,String datasetVersion,String groundTruthSha256,
            String bundleSha256,String strategy,String model,String modelDigest,String configSha256,
            String environment,String codeRevision,Instant startedAt,int datasetSamples,int evaluatedSamples,
            boolean complete, List<String> coverageTags, EvaluationMetrics.Score metrics,
            Map<String,EvaluationMetrics.Score> byDocumentType,Map<String,EvaluationMetrics.Score> byProcessingMode) {}
    public record Report(String status,List<RunReport> runs,boolean comparable,String comparisonReason,
            String metricVersion,List<String> notes) {}

    public Report report(String model,String strategy,Instant from,Instant to) {
        if(from!=null&&to!=null&&from.isAfter(to))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_DATE_RANGE");
        if(strategy!=null&&!Set.of("A","B","C").contains(strategy))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_STRATEGY");
        if(directory==null||directory.isBlank())return report("NOT_CONFIGURED",List.of());
        try {
            Path root=Path.of(directory).toRealPath();
            Path bundle=safeFile(root,root.resolve("benchmark.json"));
            if(Files.size(bundle)>8_000_000)throw invalid();
            byte[] bytes=Files.readAllBytes(bundle);
            EvaluationData data=json.readValue(bytes,EvaluationData.class);
            validate(root,data);
            String truthHash=hash(json.writeValueAsBytes(data.samples().stream()
                    .sorted(Comparator.comparing(EvaluationData.Sample::id)).toList()));
            String bundleHash=hash(bytes);
            var reports=new ArrayList<RunReport>();
            for(var run:data.runs()) {
                if(model!=null&&!model.equals(run.model()))continue;
                if(strategy!=null&&!strategy.equals(run.strategy()))continue;
                if(from!=null&&run.startedAt().isBefore(from)||to!=null&&run.startedAt().isAfter(to))continue;
                Set<String> ids=new HashSet<>();run.results().forEach(r->ids.add(r.sampleId()));
                var samples=data.samples().stream().filter(s->ids.contains(s.id())).toList();
                var groups=new TreeMap<String,EvaluationMetrics.Score>();
                for(String type:samples.stream().map(EvaluationData.Sample::documentType).distinct().sorted().toList()) {
                    var subset=samples.stream().filter(s->type.equals(s.documentType())).toList();
                    Set<String> subsetIds=new HashSet<>();subset.forEach(s->subsetIds.add(s.id()));
                    groups.put(type,EvaluationMetrics.calculate(subset,run.results().stream().filter(r->subsetIds.contains(r.sampleId())).toList()));
                }
                var modes=new TreeMap<String,EvaluationMetrics.Score>();
                for(String mode:List.of("TEXT","VISION_FALLBACK","UNKNOWN")) {
                    var subsetResults=run.results().stream().filter(r->mode.equals(r.visionFallback()==null?"UNKNOWN":r.visionFallback()?"VISION_FALLBACK":"TEXT")).toList();
                    if(subsetResults.isEmpty())continue;
                    Set<String> subsetIds=new HashSet<>();subsetResults.forEach(r->subsetIds.add(r.sampleId()));
                    modes.put(mode,EvaluationMetrics.calculate(samples.stream().filter(s->subsetIds.contains(s.id())).toList(),subsetResults));
                }
                reports.add(new RunReport(run.id(),data.datasetId(),data.datasetVersion(),truthHash,bundleHash,
                        run.strategy(),run.model(),run.modelDigest(),run.configSha256(),run.environment(),run.codeRevision(),
                        run.startedAt(),data.samples().size(),samples.size(),samples.size()==data.samples().size(),
                        samples.stream().flatMap(s->s.tags().stream()).distinct().sorted().toList(),
                        EvaluationMetrics.calculate(samples,run.results()),groups,modes));
            }
            return report(reports.isEmpty()?"NO_TEST_DATA":"AVAILABLE",reports);
        } catch(ResponseStatusException e) { throw e; }
        catch(IOException|RuntimeException e) {
            // No filesystem path, document values, raw model payloads or stack trace in API.
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"EVALUATION_DATA_INVALID_OR_UNAVAILABLE");
        }
    }
    private Report report(String status,List<RunReport> runs) {
        boolean same=!runs.isEmpty()&&runs.stream().allMatch(r->r.complete()&&r.datasetSamples()>0
                &&r.groundTruthSha256().equals(runs.getFirst().groundTruthSha256())
                &&r.modelDigest().equals(runs.getFirst().modelDigest())
                &&r.environment().equals(runs.getFirst().environment())
                &&r.codeRevision().equals(runs.getFirst().codeRevision()));
        boolean allStrategies=runs.stream().map(RunReport::strategy).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of("A","B","C"));
        boolean onePerStrategy=runs.size()==3;
        return new Report(status,List.copyOf(runs),same&&allStrategies&&onePerStrategy,
                same&&allStrategies&&onePerStrategy?"SAME_COHORT_MODEL_ENVIRONMENT_REVISION":"REQUIRE_ONE_COMPLETE_RUN_PER_A_B_C_WITH_SAME_COHORT_MODEL_ENVIRONMENT_REVISION",
                "exact-nfc-ws-v1",List.of("Confidence không phải accuracy.",
                "NEED_REVIEW là xử lý hoàn tất cần kiểm tra; SUCCESS không có nghĩa dữ liệu đúng hoặc đã duyệt.",
                "Chỉ số chỉ dựa trên ground truth thủ công và bằng chứng thực nghiệm; không lấy dữ liệu nghiệp vụ làm chuẩn.",
                "Thiếu thời gian/fallback/attempts trả null và số mẫu quan sát, không suy diễn.",
                "Nhóm Text/Vision là nhánh xử lý thực đo, có độ khó đầu vào khác nhau; không phải so sánh nhân quả trên cùng mẫu.",
                "Chi tiết hàng hóa so sánh ô theo thứ tự dòng đã chốt; dòng thừa được báo riêng."));
    }
    private void validate(Path root,EvaluationData data)throws IOException {
        if(data==null||data.schemaVersion()!=1||!label(data.datasetId())||!label(data.datasetVersion())
                ||data.samples()==null||data.runs()==null||data.samples().size()>500||data.runs().size()>20)throw invalid();
        Map<String,EvaluationData.Sample> samples=new HashMap<>();
        Set<String> assets=new HashSet<>();
        for(var sample:data.samples()) {
            if(sample==null||!label(sample.id())||!label(sample.documentType())||sample.documentType().equals("__NO_PREDICTION__")
                    ||!label(sample.verifiedBy())||sample.verifiedAt()==null||sample.verifiedAt().isAfter(Instant.now())
                    ||sample.tags()==null||sample.tags().size()>20||sample.tags().stream().anyMatch(t->!label(t))
                    ||samples.putIfAbsent(sample.id(),sample)!=null||!assets.add(sample.assetSha256()))throw invalid();
            verifyArtifact(root,"assets",sample.assetSha256(),".bin");
            text(sample.ocrText());fields(sample.fields(),false);fields(sample.ocrFields(),false);items(sample.items(),false);
        }
        Set<String> runIds=new HashSet<>();
        for(var run:data.runs()) {
            if(run==null||!label(run.id())||!runIds.add(run.id())||!Set.of("A","B","C").contains(Objects.toString(run.strategy(),""))
                    ||!label(run.model())||!digest(run.modelDigest())||!digest(run.configSha256())||!label(run.codeRevision())
                    ||run.environment()==null||run.environment().isBlank()||run.environment().length()>1000
                    ||run.startedAt()==null||run.startedAt().isAfter(Instant.now())||run.results()==null||run.results().size()>500)throw invalid();
            // Configuration and environment must be retained as evidence, not just labels entered in the UI.
            verifyArtifact(root,"configs",run.configSha256(),".json");
            var config=json.readTree(Files.readAllBytes(root.resolve("configs").resolve(run.configSha256()+".json")));
            for(var entry:Map.of("strategy",run.strategy(),"model",run.model(),"modelDigest",run.modelDigest(),
                    "environment",run.environment(),"codeRevision",run.codeRevision()).entrySet())
                if(!entry.getValue().equals(config.path(entry.getKey()).asText()))throw invalid();
            if(!config.path("settings").isObject()||config.path("settings").isEmpty())throw invalid();
            Set<String> ids=new HashSet<>();
            long editBudget=0;
            for(var result:run.results()) {
                var sample=result==null?null:samples.get(result.sampleId());
                if(sample==null||!ids.add(result.sampleId())||!sample.assetSha256().equals(result.assetSha256())
                        ||!Set.of("SUCCESS","FAILED").contains(Objects.toString(result.outcome(),""))
                        ||"FAILED".equals(result.outcome())&&!label(result.failureCode())
                        ||result.durationMs()!=null&&(result.durationMs()<0||result.durationMs()>86_400_000)
                        ||result.attemptCount()!=null&&(result.attemptCount()<1||result.attemptCount()>100)
                        ||result.predictedType()!=null&&(!label(result.predictedType())||result.predictedType().equals("__NO_PREDICTION__")))throw invalid();
                verifyArtifact(root,"evidence",result.evidenceSha256(),".json");
                var evidence=json.readTree(Files.readAllBytes(root.resolve("evidence").resolve(result.evidenceSha256()+".json")));
                var expected=json.readTree(json.writeValueAsBytes(result));
                ((com.fasterxml.jackson.databind.node.ObjectNode)expected).remove("evidenceSha256");
                if(!expected.equals(evidence))throw invalid();
                text(result.ocrText());fields(result.fields(),true);fields(result.ocrFields(),true);items(result.items(),true);
                editBudget+=(long)(sample.ocrText()==null?0:sample.ocrText().length())*(result.ocrText()==null?0:result.ocrText().length());
                if(editBudget>100_000_000L)throw invalid();
            }
        }
    }
    private void verifyArtifact(Path root,String folder,String digest,String suffix)throws IOException {
        if(!digest(digest))throw invalid();
        Path path=safeFile(root,root.resolve(folder).resolve(digest+suffix));
        if(Files.size(path)>25_000_000||!hash(Files.readAllBytes(path)).equals(digest))throw invalid();
    }
    private Path safeFile(Path root,Path path)throws IOException {
        if(Files.isSymbolicLink(path)||!path.toRealPath().startsWith(root)||!Files.isRegularFile(path))throw invalid();
        return path;
    }
    private void text(String s) { if(s!=null&&s.length()>20_000)throw invalid(); }
    private void fields(Map<String,String> fields,boolean prediction) {
        if(fields==null)return;if(fields.size()>100)throw invalid();
        fields.forEach((key,value)->{if(!label(key)||!prediction&&(value==null||value.isBlank())||value!=null&&value.length()>2000)throw invalid();});
    }
    private void items(List<Map<String,String>> items,boolean prediction) {
        if(items==null)return;if(items.size()>500)throw invalid();
        for(var row:items){if(row==null||!prediction&&row.isEmpty())throw invalid();fields(row,prediction);}
    }
    private boolean label(String s) { return s!=null&&!s.isBlank()&&s.matches("[\\p{L}\\p{N}_.:@/ +()\\-]{1,160}"); }
    private boolean digest(String s) { return s!=null&&s.matches("[a-f0-9]{64}"); }
    static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"EVALUATION_DATA_INVALID_OR_UNAVAILABLE"); }
}
