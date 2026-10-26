package com.example.invoice.evaluation;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CostCategoryMetricsTest {
    private EvaluationData.Sample sample(String id,String taxonomy,String category) {
        return new EvaluationData.Sample(id,"a".repeat(64),"INVOICE",List.of(),"human",Instant.EPOCH,
                null,null,null,null,taxonomy,category);
    }
    private EvaluationData.Result result(String id,String category) {
        return new EvaluationData.Result(id,"a".repeat(64),"b".repeat(64),"SUCCESS",null,null,null,null,
                null,null,null,null,"INVOICE",category);
    }
    @Test void categoriesAreNotDocumentTypesAndMissingPredictionsCountAgainstAccuracy() {
        var score=EvaluationMetrics.calculate(List.of(sample("1","cost-v1","OFFICE"),sample("2","cost-v1","OFFICE"),
                sample("3","cost-v1","TRANSPORT")),List.of(result("1","OFFICE"),result("2","TRANSPORT"),result("3",null)));
        assertEquals(1.0,score.classificationAccuracy());
        var category=score.costCategoryMetrics().get("cost-v1");
        assertEquals(3,category.samples()); assertEquals(1,category.correct()); assertEquals(1,category.missing());
        assertEquals(1.0/3,category.accuracy()); assertEquals(1.0/3,category.macroF1());
        assertEquals(1L,category.confusionMatrix().get("TRANSPORT").get("__NO_PREDICTION__"));
    }
    @Test void taxonomiesAndUnexecutedSamplesAreNeverMixed() {
        var score=EvaluationMetrics.calculate(List.of(sample("1","company-costs-v1","A"),
                sample("2","other-taxonomy-v1","A"),sample("3","company-costs-v1","A")),
                List.of(result("1","A"),result("2","B")));
        assertEquals(2,score.costCategoryMetrics().size());
        assertEquals(1,score.costCategoryMetrics().get("company-costs-v1").samples());
        assertEquals(1.0,score.costCategoryMetrics().get("company-costs-v1").accuracy());
        assertEquals(0.0,score.costCategoryMetrics().get("other-taxonomy-v1").accuracy());
        assertTrue(EvaluationMetrics.calculate(List.of(),List.of()).costCategoryMetrics().isEmpty());
    }
}
