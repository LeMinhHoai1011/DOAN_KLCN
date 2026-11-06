package com.example.invoice.evaluation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.invoice.evaluation.LiveEvaluationData.Label;
import com.example.invoice.evaluation.LiveEvaluationData.Sample;
import com.example.invoice.evaluation.LiveEvaluationData.State;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class LiveEvaluationScoringTest {
    static Map<String,String> row(String name,String quantity,String price,String amount,String rate,String tax) {
        var cells=new LinkedHashMap<String,String>();cells.put("productName",name);cells.put("quantity",quantity);cells.put("unitPrice",price);cells.put("amount",amount);cells.put("taxRate",rate);cells.put("taxAmount",tax);return cells;
    }
    static Sample sample() {
        var sample=new Sample();sample.setDocumentId(5L);sample.setCompanyId(1L);sample.setDocumentType("VAT_INVOICE");
        LiveEvaluationData.FIELDS.forEach(key->sample.getFields().put(key,new Label(State.ABSENT,null)));
        sample.getFields().put("invoiceNumber",new Label(State.PRESENT,"0001"));
        sample.getFields().put("invoiceDate",new Label(State.PRESENT,"01/11/2026"));
        sample.getFields().put("subtotal",new Label(State.PRESENT,"100.000"));
        sample.getFields().put("vatAmount",new Label(State.PRESENT,"8.000"));
        sample.getFields().put("totalAmount",new Label(State.PRESENT,"108.000"));
        sample.setItemsState(State.PRESENT);sample.setItems(List.of(row("Giay","2","50.000","100.000","8%","8.000")));
        sample.setCategory(new Label(State.PRESENT,"OFFICE"));sample.setConfirmedBy("reviewer");sample.setConfirmedAt(Instant.EPOCH);
        return sample;
    }
    static LivePrediction prediction(Map<String,String> fields,List<Map<String,String>> items) {
        return new LivePrediction(9L,3L,"model","VAT_INVOICE","OFFICE",fields,items,"text",1500L,LocalDateTime.now());
    }
    static Map<String,String> correctFields() {
        return new LinkedHashMap<>(Map.of("invoiceNumber","0001","invoiceDate","2026-11-01","subtotal","100000.00","vatAmount","8000","totalAmount","108000.0"));
    }
    static List<Map<String,String>> correctRows() { return List.of(row("Giay","2","50000","100000.00","0.08","8000")); }
    static EvaluationMetrics.Score score(Sample sample,LivePrediction prediction) {
        return EvaluationMetrics.calculate(List.of(LiveEvaluationScoring.toSample(sample)),List.of(LiveEvaluationScoring.toResult(sample,prediction)));
    }

    @Test void equalValuesInDifferentFormatsAreCorrect() {
        var score=score(sample(),prediction(correctFields(),correctRows()));
        assertEquals(1,score.fieldTotals().precision());assertEquals(1,score.fieldTotals().recall());
        assertEquals(1,score.itemCells().accuracy());assertEquals(1,score.exactDocuments());
    }

    @Test void wrongTotalStaysWrongAgainstTheConfirmedLabel() {
        var fields=correctFields();fields.put("totalAmount","150000");
        var score=score(sample(),prediction(fields,correctRows()));
        assertEquals(1,score.fields().get("totalAmount").incorrect());assertEquals(0,score.exactDocuments());
    }

    @Test void absentFieldWithAiValueIsSpuriousAndUnreadableIsExcluded() {
        var sample=sample();sample.getFields().put("sellerName",new Label(State.UNREADABLE,null));
        var fields=correctFields();fields.put("buyerName","Invented");fields.put("sellerName","Maybe");
        var score=score(sample,prediction(fields,correctRows()));
        assertEquals(1,score.fields().get("buyerName").spurious());
        assertNull(score.fields().get("sellerName"));
        assertEquals(5,score.fieldTotals().expected());
    }

    @Test void fieldTheAiOmittedCountsAsMissing() {
        var fields=correctFields();fields.remove("invoiceNumber");
        assertEquals(1,score(sample(),prediction(fields,correctRows())).fields().get("invoiceNumber").missing());
    }

    @Test void machineQuantityIsNotReadAsGroupedThousands() {
        var sample=sample();sample.setItems(List.of(row("Giay","1,125","80.000","90.000","8%","7.200")));
        var rows=List.of(row("Giay","1.125","80000","90000","0.08","7200"));
        assertEquals(1,score(sample,prediction(correctFields(),rows)).itemCells().accuracy());
    }

    @Test void onePercentStaysOnePercentOnBothSides() {
        assertEquals("1%",LiveEvaluationScoring.canonical("taxRate","1%"));
        assertEquals("1%",LiveEvaluationScoring.canonical("taxRate","0,01"));
        assertEquals("10%",LiveEvaluationScoring.canonical("taxRate","10"));
    }

    @Test void skippedRowDoesNotShiftLaterRows() {
        var a=row("A","1","10","10","8%","1");var b=row("B","2","20","40","8%","3");var c=row("C","3","30","90","8%","7");
        var aligned=LiveEvaluationScoring.align(List.of(a,b,c),List.of(a,c));
        assertEquals(List.of(a,Map.of(),c),aligned);
    }

    @Test void extraRowsMoveToTheEndAndScoreAsExtra() {
        var a=row("A","1","10","10","8%","1");var b=row("B","2","20","40","8%","3");var noise=row("Khac","9","9","9","0%","9");
        assertEquals(List.of(a,b,noise),LiveEvaluationScoring.align(List.of(a,b),List.of(a,noise,b)));
    }

    @Test void completelyDifferentRowsStayUnmatched() {
        var a=row("A","1","10","10","8%","1");var other=row("Z","9","9","9","9%","9");
        assertEquals(List.of(Map.of(),other),LiveEvaluationScoring.align(List.of(a),List.of(other)));
    }

    @Test void reconciliationDraftUsesLabelsAndSkipsUnfairCases() {
        var sample=sample();
        var draft=LiveEvaluationScoring.reconciliationDraft(sample);
        assertEquals(Set.of("MATCHED"),new HashSet<>(draft.values()));assertTrue(draft.containsKey("ITEM_1_AMOUNT"));
        sample.getFields().put("totalAmount",new Label(State.UNREADABLE,null));
        assertNull(LiveEvaluationScoring.reconciliationDraft(sample));
        var retail=sample();retail.setDocumentType("RETAIL_INVOICE");
        assertNull(LiveEvaluationScoring.reconciliationDraft(retail));
    }

    @Test void predictionOnExtractedValuesIsComparedWithConfirmedReconciliation() {
        var sample=sample();sample.setReconciliation(LiveEvaluationScoring.reconciliationDraft(sample));
        var wrong=List.of(row("Giay","2","50000","900000","0.08","8000"));
        var reconciliation=score(sample,prediction(correctFields(),wrong)).reconciliation();
        assertEquals(0,reconciliation.correctDocuments());assertEquals(3,reconciliation.confusionMatrix().get("MATCHED").get("MISMATCH"));
    }

    @Test void categoryUsesCompanyTaxonomyAndOcrScoresOnlyWithTranscript() {
        var sample=sample();
        var score=score(sample,prediction(correctFields(),correctRows()));
        assertEquals(1,score.costCategoryMetrics().get("company-1").accuracy());assertNull(score.cer());
        sample.setOcrText("text");assertEquals(0,score(sample,prediction(correctFields(),correctRows())).cer());
    }
}
