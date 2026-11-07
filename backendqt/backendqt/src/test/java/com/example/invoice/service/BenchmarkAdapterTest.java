package com.example.invoice.service;

import com.example.invoice.ai.*;
import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.*;
import com.example.invoice.exception.AiProviderException;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BenchmarkAdapterTest {
    static final String JSON="""
      {"documentType":"RETAIL_INVOICE","classificationConfidence":0.9,"invoice":
      {"sellerName":"TEST SHOP","invoiceDate":"2026-11-05","totalAmount":1100000,
       "items":[{"productName":"A","quantity":1,"unitPrice":1000000,"amount":1000000}]},"fields":[],"warnings":[]}
      """;
    final AiProperties properties=new AiProperties();
    final AdaptiveOcrService ocr=mock(AdaptiveOcrService.class);
    final AiProcessingService ai=mock(AiProcessingService.class);
    final PdfImageConversionService pdf=new PdfImageConversionService(properties);
    final BenchmarkAdapter.Options options=new BenchmarkAdapter.Options(List.of("RETAIL_INVOICE"),List.of(),null,null);
    BenchmarkAdapter adapter(AdaptiveOcrService service) {
        return new BenchmarkAdapter(service,pdf,ai,properties,new AiDocumentResultValidator(),new OcrTextNormalizer(properties),new OcrLayoutReconstructor());
    }
    AiDocumentResult result(){return AiDocumentResultParser.parse("ollama","qwen3-vl:8b",JSON,JSON,1);}
    void imageOcr() {
        when(ocr.recognize(any(),anyString(),anyInt())).thenAnswer(inv->{
            var page=new OcrPageResult(inv.getArgument(2),800,1000,"TEST SHOP total 1100000",10,List.of(),2,null,"eng");
            return new AdaptiveOcrService.AdaptiveOcrResult(page,inv.getArgument(0),inv.getArgument(1),OcrProcessingProfile.NORMAL,.2,1,null,null);
        });
    }
    byte[] pdf(boolean text, int count) throws Exception {
        try(var document=new PDDocument();var out=new ByteArrayOutputStream()) {
            for(int n=0;n<count;n++) {
                var page=new PDPage();document.addPage(page);
                if(text)try(var stream=new PDPageContentStream(document,page)){
                    stream.beginText();stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),14);stream.newLineAtOffset(40,700);
                    stream.showText("INVOICE TEST SHOP DATE 2026-11-05 TOTAL 1100000 TAX 100000 SAMPLE PAGE "+n);stream.endText();
                }
            }
            document.save(out);return out.toByteArray();
        }
    }
    @Test void digitalPdfUsesTextWithoutOcrAndKeepsSystemAggregation() throws Exception {
        when(ai.analyzeTextDocument(any())).thenReturn(result());
        var output=adapter(ocr).run(pdf(true,2),"application/pdf",true,options);
        assertEquals("SUCCESS",output.status());assertEquals(2,output.calls().size());
        assertTrue(output.calls().stream().allMatch(c->c.mode().equals("TEXT")));
        verifyNoInteractions(ocr);verify(ai,times(2)).analyzeTextDocument(any());
        // The current production aggregator deduplicates identical cross-page items. Do not hide this behavior.
        assertEquals(1,output.parsed().invoice().items().size());
        assertTrue(output.ocr().pages().stream().allMatch(p->p.language().equals("pdf-text-layer")));
    }
    @Test void scanPdfUsesSameVisionFallbackAndOneOcrPerPage() throws Exception {
        imageOcr();when(ai.analyzeDocument(any())).thenReturn(result());
        var output=adapter(ocr).run(pdf(false,2),"application/pdf",true,options);
        assertEquals("SUCCESS",output.status());assertEquals(2,output.calls().size());
        verify(ocr,times(2)).recognize(any(),eq("image/png"),anyInt());
        verify(ai,never()).analyzeTextDocument(any());
        assertEquals(BenchmarkAdapter.hash(AiDocumentPromptFactory.createWithCategories(options.documentTypes(),List.of(),null,null,null)),output.calls().getFirst().promptHash());
    }
    @Test void failuresAreNotEmptySuccessfulExtractions() {
        imageOcr();when(ai.analyzeDocument(any())).thenThrow(new AiProviderException("OLLAMA_TIMEOUT: private details"));
        var output=adapter(ocr).run(new byte[]{1},"image/png",true,options);
        assertEquals("FAILED",output.status());assertEquals("OLLAMA_TIMEOUT",output.errorCode());assertNotNull(output.ocr());
        assertNull(output.normalized());assertEquals("VISION",output.calls().getFirst().mode());
    }
    @Test void goodOcrUsesTextAndInvalidTextJsonFallsBackOnceToVision() {
        var words=List.of(new OcrWord("w1","INVOICE",95,.1,.1,.2,.05),new OcrWord("w2","TOTAL",95,.1,.2,.2,.05));
        var page=new OcrPageResult(1,800,1000,"INVOICE TOTAL",95,words,1,null,"vie+eng");
        when(ocr.recognize(any(),anyString(),anyInt())).thenReturn(new AdaptiveOcrService.AdaptiveOcrResult(page,new byte[]{1},"image/png",OcrProcessingProfile.NORMAL,.95,1,null,null));
        when(ocr.evaluateDetailed(any())).thenReturn(new AdaptiveOcrService.OcrQualityEvaluation(.95,.95,1,1,1,1,1,0,20));
        when(ai.analyzeTextDocument(any())).thenReturn(result());
        var first=adapter(ocr).run(new byte[]{1},"image/png",true,options);
        assertEquals("TEXT",first.calls().getFirst().mode());verify(ai,never()).analyzeDocument(any());
        when(ai.analyzeTextDocument(any())).thenThrow(new AiProviderException("OLLAMA_INVALID_JSON: malformed"));
        when(ai.analyzeDocument(any())).thenReturn(result());
        var fallback=adapter(ocr).run(new byte[]{1},"image/png",true,options);
        assertEquals("SUCCESS",fallback.status());assertEquals(List.of("TEXT","VISION"),fallback.calls().stream().map(BenchmarkAdapter.Call::mode).toList());
    }
    @Test void invalidSchemaAndUnreadableOcrAreFailures() {
        imageOcr();when(ai.analyzeDocument(any())).thenThrow(new AiProviderException("AI_JSON_PARSE_ERROR: invalid"));
        assertEquals("FAILED",adapter(ocr).run(new byte[]{1},"image/png",true,options).status());
        var empty=OcrPageResult.unavailable(1,"failed",1);
        when(ocr.recognize(any(),anyString(),anyInt())).thenReturn(new AdaptiveOcrService.AdaptiveOcrResult(empty,new byte[]{1},"image/png",OcrProcessingProfile.NORMAL,0,1,null,null));
        assertEquals("OCR_UNREADABLE",adapter(ocr).run(new byte[]{1},"image/png",false,options).errorCode());
    }
    @Test void actualTesseractRecognizesGeneratedPngAndJpgWithoutDatabase() throws Exception {
        properties.getOcr().setLanguage("eng");
        var real=new AdaptiveOcrService(properties,new ImageQualityAnalyzer(),new OpenCvImageProcessor(properties),new Tess4jOcrService(properties));
        var image=new BufferedImage(1200,600,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setColor(java.awt.Color.WHITE);g.fillRect(0,0,1200,600);g.setColor(java.awt.Color.BLACK);g.setFont(new java.awt.Font("Arial",java.awt.Font.PLAIN,40));
        g.drawString("INVOICE TEST SHOP",60,100);g.drawString("TOTAL 1100000 DATE 2026-11-05",60,200);g.dispose();
        for(String format:List.of("png","jpg")) {
            var out=new ByteArrayOutputStream();ImageIO.write(image,format,out);
            var result=adapter(real).run(out.toByteArray(),"image/"+format,false,options);
            assertEquals("SUCCESS",result.status());assertTrue(result.ocr().rawText().contains("INVOICE"));
        }
        try(var scanned=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            var embedded=org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(scanned,image);
            for(int n=0;n<2;n++) {
                var page=new PDPage();scanned.addPage(page);
                try(var stream=new PDPageContentStream(scanned,page)){stream.drawImage(embedded,20,350,570,285);}
            }
            scanned.save(bytes);
            var scan=adapter(real).run(bytes.toByteArray(),"application/pdf",false,options);
            assertEquals("SUCCESS",scan.status());assertEquals(2,scan.ocr().pages().size());
            assertTrue(scan.ocr().pages().stream().allMatch(p->p.text().contains("INVOICE")));
        }
        verifyNoInteractions(ai);
    }
}
