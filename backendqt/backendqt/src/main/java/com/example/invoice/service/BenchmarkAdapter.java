package com.example.invoice.service;

import com.example.invoice.ai.*;
import com.example.invoice.ai.AiDocumentPromptFactory.AccountingCategoryOption;
import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.*;
import com.example.invoice.entity.Company;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** File-only adapter. Deliberately holds no repository, storage, log or persistence service. */
@Service
@ConditionalOnProperty(name="benchmark.enabled", havingValue="true")
public class BenchmarkAdapter {
    public record Options(List<String> documentTypes, List<AccountingCategoryOption> categories,
                          String companyName, String companyTaxCode) {
        public Options {
            if (documentTypes == null || documentTypes.isEmpty()) throw new IllegalArgumentException("DOCUMENT_TYPES_REQUIRED");
            documentTypes = List.copyOf(documentTypes);
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }
    public record Call(String mode, String promptHash, AiDocumentResult result) {}
    public record Output(String status, String errorCode, OcrDocumentResult ocr, AiDocumentResult parsed,
                         AiDocumentResultValidator.ValidatedAiDocumentResult normalized, List<Call> calls,
                         long ocrMs, long totalMs) {
        @Override public String toString() { return "BenchmarkOutput[status="+status+", errorCode="+errorCode+"]"; }
    }
    private final AdaptiveOcrService ocr;
    private final PdfImageConversionService pdf;
    private final AiProcessingService ai;
    private final AiProperties properties;
    private final AiDocumentResultValidator validator;
    private final OcrTextNormalizer normalizer;
    private final OcrLayoutReconstructor layout;
    public BenchmarkAdapter(AdaptiveOcrService ocr, PdfImageConversionService pdf, AiProcessingService ai,
            AiProperties properties, AiDocumentResultValidator validator, OcrTextNormalizer normalizer,
            OcrLayoutReconstructor layout) {
        this.ocr=ocr; this.pdf=pdf; this.ai=ai; this.properties=properties;
        this.validator=validator; this.normalizer=normalizer; this.layout=layout;
    }
    public synchronized Output run(byte[] bytes, String mime, boolean withAi, Options options) {
        long start=System.nanoTime(), ocrNs=0;
        var calls=new ArrayList<Call>();
        // Delegate to the same provider and capture only successful structured responses.
        var recording=new AiProcessingService(properties, List.of()) {
            @Override public AiDocumentResult analyzeDocument(AiDocumentRequest r) {
                int index=calls.size();calls.add(new Call("VISION",hash(r.prompt()),null));
                var result=ai.analyzeDocument(r); calls.set(index,new Call("VISION",hash(r.prompt()),result)); return result;
            }
            @Override public AiDocumentResult analyzeTextDocument(AiTextRequest r) {
                int index=calls.size();calls.add(new Call("TEXT",hash(r.prompt()),null));
                var result=ai.analyzeTextDocument(r); calls.set(index,new Call("TEXT",hash(r.prompt()),result)); return result;
            }
        };
        // All persistence dependencies are null. Only the pure package-level helpers below are called.
        var pipeline=new DocumentAiProcessingService(null,null,null,recording,validator,null,null,pdf,ocr,
                properties,normalizer,layout,null);
        var company=new Company(); company.setCompanyName(options.companyName()); company.setTaxCode(options.companyTaxCode());
        var codes=options.categories().stream().map(AccountingCategoryOption::code).toList();
        var pages=new ArrayList<OcrPageResult>(); var results=new ArrayList<AiDocumentResult>();
        OcrDocumentResult document=null;
        try {
            long t=System.nanoTime();
            var textPages="application/pdf".equals(mime) ? pdf.extractTextPages(bytes) : List.<PdfImageConversionService.PdfTextPage>of();
            boolean allText=!textPages.isEmpty() && textPages.stream().allMatch(PdfImageConversionService.PdfTextPage::usable);
            var images="application/pdf".equals(mime) ? (allText ? List.<PdfPageImage>of() : pdf.convert(bytes))
                    : List.of(new PdfPageImage(1,bytes,mime));
            ocrNs+=System.nanoTime()-t;
            int count="application/pdf".equals(mime) ? textPages.size() : 1;
            for(int n=1;n<=count;n++) {
                final int index=n;
                var nativePage=textPages.stream().filter(p->p.pageNumber()==index&&p.usable()).findFirst();
                t=System.nanoTime();
                OcrPageResult page; byte[] selected=null; String selectedMime=null;
                if(nativePage.isPresent()) page=new OcrPageResult(n,0,0,nativePage.get().text(),Float.NaN,List.of(),0,null,"pdf-text-layer");
                else {
                    var image=images.stream().filter(p->p.pageNumber()==index).findFirst().orElseThrow();
                    var adaptive=ocr.recognize(image.bytes(),image.contentType(),n);
                    page=pipeline.reconstructLayout(adaptive.page()); selected=adaptive.selectedImageBytes(); selectedMime=adaptive.selectedContentType();
                }
                pages.add(page); ocrNs+=System.nanoTime()-t; document=pipeline.aggregateOcr(pages);
                if(withAi) results.add(nativePage.isPresent()
                    ? pipeline.analyzeDigitalPdfText("benchmark#page-"+n,page.text(),options.documentTypes(),options.categories(),company)
                    : pipeline.analyzeSingleImage("benchmark#page-"+n,selectedMime,selected,options.documentTypes(),codes,options.categories(),company,page));
            }
            if(!withAi) return new Output(document!=null&&pages.stream().allMatch(OcrPageResult::successful)?"SUCCESS":"FAILED",
                    pages.stream().allMatch(OcrPageResult::successful)?null:"OCR_UNREADABLE",document,null,null,calls,ocrNs/1000000,elapsed(start));
            var parsed=pipeline.resolveCompanyRole(pipeline.aggregatePageResults(results),company);
            parsed=pipeline.addMissingInvoiceItemsWarning(parsed,document);
            var normalized=validator.validate(parsed,Set.copyOf(options.documentTypes()),Set.copyOf(codes));
            return new Output("SUCCESS",null,document,parsed,normalized,List.copyOf(calls),ocrNs/1000000,elapsed(start));
        } catch(RuntimeException e) {
            String message=Objects.toString(e.getMessage(),"");
            String code=message.split(":",2)[0];
            if(!code.matches("[A-Z][A-Z0-9_]{2,60}")) code="BENCHMARK_PROCESSING_FAILED";
            return new Output("FAILED",code,document,null,null,List.copyOf(calls),ocrNs/1000000,elapsed(start));
        }
    }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    private static long elapsed(long start) { return (System.nanoTime()-start)/1000000; }
}
