package com.example.invoice.evaluation;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationMetricsTest {
    static EvaluationData.Sample sample(String id,String type,String text,Map<String,String> fields,List<Map<String,String>> items) {
        return new EvaluationData.Sample(id,"a".repeat(64),type,List.of("VIETNAMESE"),"human-reviewer",Instant.EPOCH,text,fields,fields,items);
    }
    static EvaluationData.Result result(String id,String predicted,String text,Map<String,String> fields,List<Map<String,String>> items) {
        return new EvaluationData.Result(id,"a".repeat(64),"b".repeat(64),"SUCCESS",null,100L,false,1,text,fields,fields,items,predicted);
    }
    @Test void emptyDataIsUnknownRatherThanZeroOrPerfect() {
        var score=EvaluationMetrics.calculate(List.of(),List.of());
        assertNull(score.cer());assertNull(score.wer());assertNull(score.classificationAccuracy());assertNull(score.successRate());assertNull(score.macroF1());assertNull(score.itemCells().accuracy());
    }
    @Test void unicodeNormalizationPreservesVietnameseDiacriticsAndCase() {
        var truth=sample("s","INVOICE","Hóa đơn",null,null);
        var exact=EvaluationMetrics.calculate(List.of(truth),List.of(result("s","INVOICE","Ho\u0301a   đơn",null,null)));
        assertEquals(0,exact.cer());assertEquals(0,exact.wer());
        var lost=EvaluationMetrics.calculate(List.of(truth),List.of(result("s","INVOICE","Hoa don",null,null)));
        assertTrue(lost.cer()>0);assertEquals(1,lost.wer());
    }
    @Test void corpusCerAndWerIncludeInsertionsAndCanExceedOne() {
        var score=EvaluationMetrics.calculate(List.of(sample("s","INVOICE","a",null,null)),List.of(result("s","INVOICE","abc def ghi",null,null)));
        assertEquals(10,score.characterEdits());assertEquals(1,score.referenceCharacters());assertEquals(10,score.cer());assertEquals(3,score.wer());
    }
    @Test void fieldsSeparateMissingIncorrectAndCorrectWithoutConfidence() {
        var truth=sample("s","INVOICE",null,Map.of("number","A01","total","100","seller","Đơn vị"),null);
        var score=EvaluationMetrics.calculate(List.of(truth),List.of(result("s","INVOICE",null,Map.of("number","A01","total","99","extra","invented"),null)));
        assertEquals(1,score.fields().get("number").correct());assertEquals(1,score.fields().get("total").incorrect());assertEquals(1,score.fields().get("seller").missing());assertEquals(1,score.extraFields());assertNull(score.cer());
    }
    @Test void classificationIncludesWrongLabelsAndMissingPredictions() {
        var samples=List.of(sample("1","INVOICE",null,null,null),sample("2","INVOICE",null,null,null),sample("3","CONTRACT",null,null,null));
        var score=EvaluationMetrics.calculate(samples,List.of(result("1","INVOICE",null,null,null),result("2","CONTRACT",null,null,null),result("3",null,null,null,null)));
        assertEquals(1.0/3,score.classificationAccuracy());assertEquals(1,score.classes().get("INVOICE").precision());assertEquals(.5,score.classes().get("INVOICE").recall());assertEquals(2.0/3,score.classes().get("INVOICE").f1());assertEquals(1,score.confusionMatrix().get("CONTRACT").get("__NO_PREDICTION__"));assertEquals(1.0/3,score.macroF1());
    }
    @Test void failureIsIncludedInEndToEndAccuracyNotDroppedAsSurvivor() {
        var samples=List.of(sample("1","INVOICE","a",Map.of("total","10"),null));
        var failed=new EvaluationData.Result("1","a".repeat(64),"b".repeat(64),"FAILED","AI_TIMEOUT",null,null,null,null,null,null,null,null);
        var score=EvaluationMetrics.calculate(samples,List.of(failed));
        assertEquals(1,score.failureRate());assertEquals(0,score.classificationAccuracy());assertEquals(1,score.fields().get("total").missing());assertEquals(1,score.cer());assertNull(score.averageProcessingMs());assertNull(score.visionFallbackRate());assertNull(score.retryRate());assertEquals(1,score.failuresByCode().get("AI_TIMEOUT"));
    }
    @Test void itemCellsUseStableRowOrderAndReportExtraRows() {
        var truth=sample("s","INVOICE",null,null,List.of(Map.of("name","A","quantity","2"),Map.of("name","B")));
        var score=EvaluationMetrics.calculate(List.of(truth),List.of(result("s","INVOICE",null,null,List.of(Map.of("name","A","quantity","3"),Map.of(),Map.of("name","extra")))));
        assertEquals(3,score.itemCells().expected());assertEquals(1,score.itemCells().correct());assertEquals(1,score.itemCells().missing());assertEquals(1,score.itemCells().incorrect());assertEquals(1,score.extraItemRows());
    }
    @Test void pipelineDenominatorsUseOnlyObservedTelemetry() {
        var samples=List.of(sample("1","INVOICE",null,null,null),sample("2","CONTRACT",null,null,null));
        var r=new EvaluationData.Result("2","a".repeat(64),"b".repeat(64),"SUCCESS",null,300L,true,2,null,null,null,null,"CONTRACT");
        var score=EvaluationMetrics.calculate(samples,List.of(result("1","INVOICE",null,null,null),r));
        assertEquals(200,score.averageProcessingMs());assertEquals(.5,score.visionFallbackRate());assertEquals(.5,score.retryRate());assertEquals(2,score.timedSamples());
    }
}
