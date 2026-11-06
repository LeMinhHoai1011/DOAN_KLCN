package com.example.invoice.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExtractionQualityMetricsTest {
    static final Map<String,String> HEADER=Map.of("subtotal","100","vatAmount","10","totalAmount","110");
    static final List<Map<String,String>> ROWS=List.of(Map.of("quantity","1","unitPrice","100","amount","100","taxRate","10%","taxAmount","10"));

    static EvaluationData.Sample sample(Map<String,String> fields,List<Map<String,String>> items,Map<String,String> reconciliation) {
        return new EvaluationData.Sample("s","a".repeat(64),"VAT_INVOICE",List.of("CLEAR"),"human",Instant.EPOCH,null,fields,null,items,null,null,reconciliation);
    }
    static EvaluationData.Result result(String outcome,Map<String,String> fields,List<Map<String,String>> items) {
        return new EvaluationData.Result("s","a".repeat(64),"b".repeat(64),outcome,"SUCCESS".equals(outcome)?null:"X",1L,false,1,null,null,fields,items,"VAT_INVOICE");
    }
    static EvaluationMetrics.Score score(EvaluationData.Sample sample,EvaluationData.Result result) {
        return EvaluationMetrics.calculate(List.of(sample),List.of(result));
    }

    @Test void fieldPrecisionRecallAndF1SeparateWrongMissingAndInventedValues() {
        var samples=List.of(sample(Map.of("number","A"),null,null),sample(Map.of("number","B"),null,null),sample(Map.of("number","C"),null,null),sample(Map.of("number","D"),null,null));
        var renamed=new ArrayList<EvaluationData.Sample>();var results=new ArrayList<EvaluationData.Result>();
        String[] predicted={"A","A","B",null};
        for(int i=0;i<4;i++){var s=samples.get(i);String id="s"+i;
            renamed.add(new EvaluationData.Sample(id,s.assetSha256(),s.documentType(),s.tags(),s.verifiedBy(),s.verifiedAt(),null,s.fields(),null,null));
            var fields=new HashMap<String,String>();fields.put("number",predicted[i]);
            results.add(new EvaluationData.Result(id,s.assetSha256(),"b".repeat(64),"SUCCESS",null,1L,false,1,null,null,fields,null,"VAT_INVOICE"));}
        var number=EvaluationMetrics.calculate(renamed,results).fields().get("number");
        assertEquals(1,number.correct());assertEquals(2,number.incorrect());assertEquals(1,number.missing());
        assertEquals(1d/3,number.precision(),1e-9);assertEquals(0.25,number.recall(),1e-9);assertEquals(number.accuracy(),number.recall());
        assertEquals(2*(1d/3)*0.25/(1d/3+0.25),number.f1(),1e-9);
    }

    @Test void valueOutsideTheReferenceLowersPrecisionButNotRecall() {
        var score=score(sample(Map.of("total","10"),null,null),result("SUCCESS",Map.of("total","10","invented","x"),null));
        assertEquals(1,score.fields().get("invented").spurious());assertNull(score.fields().get("invented").recall());assertNull(score.fields().get("invented").f1());
        assertEquals(1,score.fields().get("total").f1());
        assertEquals(0.5,score.fieldTotals().precision(),1e-9);assertEquals(1,score.fieldTotals().recall());
        assertEquals(0.5,score.fieldMacroPrecision(),1e-9);assertEquals(1,score.fieldMacroRecall());assertEquals(1,score.fieldMacroF1());
        assertEquals(0,score.exactDocuments());
    }

    @Test void noCorrectOrPredictedValueNeverInventsAScore() {
        var score=score(sample(Map.of("total","10"),null,null),result("SUCCESS",Map.of(),null));
        assertNull(score.fields().get("total").precision());assertEquals(0,score.fields().get("total").recall());assertNull(score.fields().get("total").f1());
        var empty=EvaluationMetrics.calculate(List.of(),List.of());
        assertNull(empty.fieldMacroPrecision());assertNull(empty.exactDocumentRate());assertNull(empty.reconciliation());
    }

    @Test void blankPredictionIsNotAnInventedValue() {
        var fields=new HashMap<String,String>();fields.put("total","10");fields.put("note"," ");
        var score=score(sample(Map.of("total","10"),null,null),result("SUCCESS",fields,null));
        assertNull(score.fields().get("note"));assertEquals(1,score.exactDocuments());
    }

    @Test void exactDocumentNeedsEveryFieldCellAndRow() {
        assertEquals(1,score(sample(HEADER,ROWS,null),result("SUCCESS",HEADER,ROWS)).exactDocuments());
        var wrongCell=List.of(Map.of("quantity","2","unitPrice","100","amount","100","taxRate","10%","taxAmount","10"));
        assertEquals(0,score(sample(HEADER,ROWS,null),result("SUCCESS",HEADER,wrongCell)).exactDocuments());
        var extraRow=new ArrayList<>(ROWS);extraRow.add(Map.of("amount","1"));
        var score=score(sample(HEADER,ROWS,null),result("SUCCESS",HEADER,extraRow));
        assertEquals(0,score.exactDocuments());assertEquals(1,score.exactEligible());assertEquals(0,score.exactDocumentRate());
        assertEquals(1,score.itemCells().spurious());assertEquals(1,score.itemCells().recall());assertEquals(5d/6,score.itemCells().precision(),1e-9);
    }

    @Test void reconciliationIsScoredAgainstConfirmedLabelsOnly() {
        assertNull(score(sample(HEADER,ROWS,null),result("SUCCESS",HEADER,ROWS)).reconciliation());
        var labels=EvaluationReconciliation.statuses(HEADER,ROWS);
        assertEquals(Set.of("MATCHED"),new HashSet<>(labels.values()));
        var score=score(sample(HEADER,ROWS,labels),result("SUCCESS",HEADER,ROWS)).reconciliation();
        assertEquals(1,score.documentAccuracy());assertEquals(1,score.checkAccuracy());assertEquals(labels.size(),score.checks());
        assertEquals(labels.size(),score.confusionMatrix().get("MATCHED").get("MATCHED"));
    }

    @Test void misreadAmountCountsAsWrongReconciliationOnExtractedValues() {
        var labels=EvaluationReconciliation.statuses(HEADER,ROWS);
        var misread=List.of(Map.of("quantity","1","unitPrice","100","amount","900","taxRate","10%","taxAmount","10"));
        var score=score(sample(HEADER,ROWS,labels),result("SUCCESS",HEADER,misread)).reconciliation();
        assertEquals(0,score.documentAccuracy());assertEquals(0,score.correctDocuments());
        assertEquals(labels.size()-3,score.correctChecks());assertEquals(3,score.confusionMatrix().get("MATCHED").get("MISMATCH"));
    }

    @Test void genuineMismatchFoundOnBothSidesIsCorrect() {
        var printed=new HashMap<>(HEADER);printed.put("totalAmount","120");
        var labels=EvaluationReconciliation.statuses(printed,ROWS);
        assertEquals("MISMATCH",labels.get("TOTAL"));
        assertEquals(1,score(sample(printed,ROWS,labels),result("SUCCESS",printed,ROWS)).reconciliation().documentAccuracy());
    }

    @Test void droppedOrAddedRowsAndFailedRunsAreWrongNotSkipped() {
        var labels=EvaluationReconciliation.statuses(HEADER,ROWS);
        var failed=score(sample(HEADER,ROWS,labels),result("FAILED",null,null)).reconciliation();
        assertEquals(0,failed.correctChecks());assertEquals(labels.size(),failed.confusionMatrix().get("MATCHED").get("__NO_PREDICTION__"));
        var extra=new ArrayList<>(ROWS);extra.add(ROWS.getFirst());
        var added=score(sample(HEADER,ROWS,labels),result("SUCCESS",HEADER,extra)).reconciliation();
        assertEquals(2,added.extraChecks());assertEquals(0,added.correctDocuments());
    }

    @Test void readableFormatsMatchAndUnreadableValuesAreMissingNotZero() {
        var grouped=EvaluationReconciliation.statuses(Map.of("subtotal","1.000.000","vatAmount","80.000","totalAmount","1.080.000"),
                List.of(Map.of("quantity","2","unitPrice","500.000,00","amount","1.000.000","taxRate","8%","taxAmount","80.000")));
        assertEquals(Set.of("MATCHED"),new HashSet<>(grouped.values()));
        var onePercent=EvaluationReconciliation.statuses(Map.of(),List.of(Map.of("amount","1000","taxRate","1%","taxAmount","10")));
        assertEquals("MATCHED",onePercent.get("ITEM_1_VAT"));
        var unreadable=EvaluationReconciliation.statuses(Map.of("subtotal","abc","vatAmount","10","totalAmount","110"),List.of());
        assertEquals("INSUFFICIENT_DATA",unreadable.get("TOTAL"));assertEquals("INSUFFICIENT_DATA",unreadable.get("ITEMS_PRESENT"));
    }
}
