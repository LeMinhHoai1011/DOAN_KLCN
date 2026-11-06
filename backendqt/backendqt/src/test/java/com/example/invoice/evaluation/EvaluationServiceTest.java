package com.example.invoice.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class EvaluationServiceTest {
    @TempDir Path directory;
    ObjectMapper json=JsonMapper.builder().addModule(new JavaTimeModule()).build();
    EvaluationData data;
    String artifact(String folder,byte[] bytes,String suffix)throws Exception {
        String hash=EvaluationService.hash(bytes);Files.createDirectories(directory.resolve(folder));Files.write(directory.resolve(folder).resolve(hash+suffix),bytes);return hash;
    }
    EvaluationData.Run run(String strategy,List<EvaluationData.Result> results)throws Exception {
        return run(strategy,"qwen3-vl","a".repeat(64),"test-only fixed CPU RAM GPU threads seed warm-up",results);
    }
    EvaluationData.Run run(String strategy,String model,String digest,String env,List<EvaluationData.Result> results)throws Exception {
        String revision="test-revision";
        String config=artifact("configs",json.writeValueAsBytes(Map.of("strategy",strategy,"model",model,"modelDigest",digest,"environment",env,"codeRevision",revision,"settings",Map.of("temperature",0,"promptSha256","b".repeat(64)))),".json");
        return new EvaluationData.Run("run-"+strategy,strategy,model,digest,config,env,revision,Instant.parse("2026-01-01T00:00:00Z"),results);
    }
    @BeforeEach void fixture()throws Exception {
        String asset=artifact("assets","unit-test artifact only, not real benchmark".getBytes(java.nio.charset.StandardCharsets.UTF_8),".bin");
        var sample=new EvaluationData.Sample("s1",asset,"INVOICE",List.of("CLEAR","VIETNAMESE"),"test-human",Instant.EPOCH,"Hóa đơn",Map.of("totalAmount","100"),null,null);
        var r=new EvaluationData.Result("s1",asset,null,"SUCCESS",null,25L,false,1,"Hóa đơn",null,Map.of("totalAmount","100"),null,"INVOICE");
        var evidence=json.readTree(json.writeValueAsBytes(r));((com.fasterxml.jackson.databind.node.ObjectNode)evidence).remove("evidenceSha256");
        ((com.fasterxml.jackson.databind.node.ObjectNode)evidence).remove("predictedCategory");
        String hash=artifact("evidence",json.writeValueAsBytes(evidence),".json");
        r=new EvaluationData.Result(r.sampleId(),asset,hash,r.outcome(),null,r.durationMs(),r.visionFallback(),r.attemptCount(),r.ocrText(),null,r.fields(),null,r.predictedType());
        data=new EvaluationData(1,"unit-only","1",List.of(sample),List.of(run("A",List.of(r)),run("B",List.of(r)),run("C",List.of(r))));write();
    }
    void write()throws Exception{Files.write(directory.resolve("benchmark.json"),json.writeValueAsBytes(data));}
    EvaluationService service(){return new EvaluationService(directory.toString(),json);}
    @Test void disabledByDefaultAndNoDatabaseAccess() {assertEquals("NOT_CONFIGURED",new EvaluationService("",json).report(null,null,null,null).status());}
    @Test void realArtifactsAreRequiredAndCompleteCohortIsComparable() {
        var report=service().report(null,null,null,null);assertTrue(report.comparable());assertEquals(3,report.runs().size());assertEquals(1,report.runs().getFirst().metrics().classificationAccuracy());assertEquals(64,report.runs().getFirst().groundTruthSha256().length());
    }
    @Test void filtersDoNotClaimPairedComparisonOrInventRows() {
        var a=service().report("qwen3-vl","A",Instant.EPOCH,Instant.parse("2026-01-02T00:00:00Z"));assertEquals(1,a.runs().size());assertFalse(a.comparable());assertEquals("NO_TEST_DATA",service().report("other",null,null,null).status());
        assertEquals("NO_TEST_DATA",service().report(null,null,Instant.parse("2026-01-02T00:00:00Z"),null).status());
    }
    @Test void invalidDateAndStrategyRejected() {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service().report(null,null,Instant.now(),Instant.EPOCH)).getStatusCode().value());
        assertEquals(400,assertThrows(ResponseStatusException.class,()->service().report(null,"D",null,null)).getStatusCode().value());
    }
    @Test void tamperedAssetFailsClosed()throws Exception {
        Files.writeString(directory.resolve("assets").resolve(data.samples().getFirst().assetSha256()+".bin"),"changed");
        assertEquals(422,assertThrows(ResponseStatusException.class,()->service().report(null,null,null,null)).getStatusCode().value());
    }
    @Test void unverifiedTruthAndConfidenceCannotBecomeAccuracy()throws Exception {
        var tree=json.readTree(Files.readAllBytes(directory.resolve("benchmark.json")));
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("samples").get(0)).putNull("verifiedBy");Files.write(directory.resolve("benchmark.json"),json.writeValueAsBytes(tree));assertThrows(ResponseStatusException.class,()->service().report(null,null,null,null));
        write();tree=json.readTree(Files.readAllBytes(directory.resolve("benchmark.json")));((com.fasterxml.jackson.databind.node.ObjectNode)tree.path("runs").get(0).path("results").get(0)).put("confidence",100);Files.write(directory.resolve("benchmark.json"),json.writeValueAsBytes(tree));assertThrows(ResponseStatusException.class,()->service().report(null,null,null,null));
    }
    EvaluationData.Sample labelled(String type,Map<String,String> reconciliation) {
        var s=data.samples().getFirst();
        return new EvaluationData.Sample(s.id(),s.assetSha256(),type,s.tags(),s.verifiedBy(),s.verifiedAt(),s.ocrText(),s.fields(),s.ocrFields(),s.items(),null,null,reconciliation);
    }
    void bundle(int schema,EvaluationData.Sample sample)throws Exception {
        var r=new EvaluationData.Result("s1",sample.assetSha256(),null,"SUCCESS",null,25L,false,1,"Hóa đơn",null,Map.of("totalAmount","100"),null,"INVOICE");
        var evidence=json.readTree(json.writeValueAsBytes(r));((com.fasterxml.jackson.databind.node.ObjectNode)evidence).remove("evidenceSha256");
        String hash=artifact("evidence",json.writeValueAsBytes(evidence),".json");
        r=new EvaluationData.Result("s1",sample.assetSha256(),hash,"SUCCESS",null,25L,false,1,"Hóa đơn",null,r.fields(),null,"INVOICE");
        data=new EvaluationData(schema,data.datasetId(),data.datasetVersion(),List.of(sample),List.of(run("A",List.of(r))));write();
    }
    @Test void reconciliationLabelsNeedSchema3AndAreScored()throws Exception {
        bundle(3,labelled("INVOICE",Map.of("TOTAL","INSUFFICIENT_DATA")));
        var score=service().report(null,null,null,null).runs().getFirst().metrics().reconciliation();
        assertEquals(1,score.samples());assertEquals(1,score.correctChecks());assertEquals(3,score.extraChecks());assertEquals(0,score.correctDocuments());
        bundle(2,labelled("INVOICE",Map.of("TOTAL","MATCHED")));assertThrows(ResponseStatusException.class,()->service().report(null,null,null,null));
    }
    @Test void invalidReconciliationLabelsFailClosed()throws Exception {
        for(var bad:List.of(labelled("INVOICE",Map.of("TOTAL","ACCEPTED")),labelled("INVOICE",Map.of()),labelled("RETAIL_INVOICE",Map.of("TOTAL","MATCHED")))) {
            bundle(3,bad);assertEquals(422,assertThrows(ResponseStatusException.class,()->service().report(null,null,null,null)).getStatusCode().value());
        }
    }
    @Test void unlabelledRunsReportNoReconciliationAndOlderGroundTruthHashIsUnchanged()throws Exception {
        bundle(2,labelled("INVOICE",null));
        var truth=json.valueToTree(data.samples());((com.fasterxml.jackson.databind.node.ObjectNode)truth.get(0)).remove("reconciliation");
        var run=service().report(null,null,null,null).runs().getFirst();
        assertNull(run.metrics().reconciliation());assertEquals(EvaluationService.hash(json.writeValueAsBytes(truth)),run.groundTruthSha256());
    }
    @Test void incompleteRunReportsCoverageAndCannotCompare()throws Exception {
        data=new EvaluationData(1,data.datasetId(),data.datasetVersion(),data.samples(),List.of(run("A",List.of())));write();
        var report=service().report(null,null,null,null);assertFalse(report.comparable());assertFalse(report.runs().getFirst().complete());assertEquals(0,report.runs().getFirst().evaluatedSamples());assertNull(report.runs().getFirst().metrics().successRate());
    }
    @Test void duplicateResultAndUnknownSampleAreRejected()throws Exception {
        var r=data.runs().getFirst().results().getFirst();data=new EvaluationData(1,data.datasetId(),data.datasetVersion(),data.samples(),List.of(run("A",List.of(r,r))));write();assertThrows(ResponseStatusException.class,()->service().report(null,null,null,null));
    }
    @Test void mismatchedEvidenceCannotBeScored()throws Exception {
        var r=data.runs().getFirst().results().getFirst();var wrong=new EvaluationData.Result(r.sampleId(),r.assetSha256(),r.evidenceSha256(),r.outcome(),null,999L,false,1,r.ocrText(),null,r.fields(),null,r.predictedType());data=new EvaluationData(1,data.datasetId(),data.datasetVersion(),data.samples(),List.of(run("A",List.of(wrong))));write();assertThrows(ResponseStatusException.class,()->service().report(null,null,null,null));
    }
    @Test void aggregateReportDoesNotLeakGroundTruthValuesOrPaths()throws Exception {
        String report=json.writeValueAsString(service().report(null,null,null,null));assertFalse(report.contains("Hóa đơn"));assertFalse(report.contains(directory.toString()));assertFalse(report.contains("test-human"));
    }
    @Test void emptyDatasetNeverQualifiesForComparison()throws Exception {
        data=new EvaluationData(1,"empty","1",List.of(),List.of(run("A",List.of()),run("B",List.of()),run("C",List.of())));write();assertFalse(service().report(null,null,null,null).comparable());
    }
    @Test void branchStatisticsAreObservedRatherThanInferredFromStrategy() {
        var run=service().report(null,"C",null,null).runs().getFirst();
        assertEquals(1,run.byProcessingMode().get("TEXT").samples());assertFalse(run.byProcessingMode().containsKey("VISION_FALLBACK"));
    }

    @Test void versionTwoCategoryEvidenceIsScoredSeparatelyAndVersionOneCannotClaimIt() throws Exception {
        var old = data.samples().getFirst();
        var sample = new EvaluationData.Sample(old.id(),old.assetSha256(),old.documentType(),old.tags(),
                old.verifiedBy(),old.verifiedAt(),old.ocrText(),old.fields(),old.ocrFields(),old.items(),"internal-costs-v1","OFFICE");
        var oldResult = data.runs().getFirst().results().getFirst();
        var predicted = new EvaluationData.Result(oldResult.sampleId(),oldResult.assetSha256(),null,"SUCCESS",null,
                25L,false,1,oldResult.ocrText(),null,oldResult.fields(),null,"INVOICE","TRANSPORT");
        var tree = json.valueToTree(predicted);
        ((com.fasterxml.jackson.databind.node.ObjectNode)tree).remove("evidenceSha256");
        String digest = artifact("evidence",json.writeValueAsBytes(tree),".json");
        var result = new EvaluationData.Result(predicted.sampleId(),predicted.assetSha256(),digest,"SUCCESS",null,
                25L,false,1,predicted.ocrText(),null,predicted.fields(),null,"INVOICE","TRANSPORT");
        data = new EvaluationData(2,"unit-only","2",List.of(sample),List.of(run("A",List.of(result)))); write();
        var metrics = service().report(null,null,null,null).runs().getFirst().metrics();
        assertEquals(1.0,metrics.classificationAccuracy());
        assertEquals(0.0,metrics.costCategoryMetrics().get("internal-costs-v1").accuracy());
        data = new EvaluationData(1,data.datasetId(),data.datasetVersion(),data.samples(),data.runs()); write();
        assertThrows(ResponseStatusException.class,()->service().report(null,null,null,null));
    }

    @Test void categoryReferenceRequiresTaxonomyAndLegacyReportsHaveNoCategoryMetrics() throws Exception {
        assertTrue(service().report(null,null,null,null).runs().getFirst().metrics().costCategoryMetrics().isEmpty());
        var old = data.samples().getFirst();
        var invalid = new EvaluationData.Sample(old.id(),old.assetSha256(),old.documentType(),old.tags(),
                old.verifiedBy(),old.verifiedAt(),null,null,null,null,null,"OFFICE");
        data = new EvaluationData(2,"unit-only","2",List.of(invalid),List.of()); write();
        assertThrows(ResponseStatusException.class,()->service().report(null,null,null,null));
    }
    @Test void ruleBaselineJoinsCohortWithItsOwnModelAndEnvironment()throws Exception {
        var r=data.runs().getFirst().results();
        data=new EvaluationData(1,data.datasetId(),data.datasetVersion(),data.samples(),List.of(run("A",r),run("B",r),run("C",r),
                run("R","rule-baseline-v1","c".repeat(64),"java; offline",r)));write();
        var report=service().report(null,null,null,null);
        assertTrue(report.comparable());assertEquals(4,report.runs().size());
        assertEquals("SAME_COHORT_MODEL_ENVIRONMENT_REVISION_WITH_RULE_BASELINE",report.comparisonReason());
        assertEquals(1,service().report(null,"R",null,null).runs().size());
        var second=run("R","rule-baseline-v1","c".repeat(64),"java; offline",r);
        second=new EvaluationData.Run("run-R2",second.strategy(),second.model(),second.modelDigest(),second.configSha256(),
                second.environment(),second.codeRevision(),second.startedAt(),second.results());
        data=new EvaluationData(1,data.datasetId(),data.datasetVersion(),data.samples(),List.of(run("A",r),run("B",r),run("C",r),
                run("R","rule-baseline-v1","c".repeat(64),"java; offline",r),second));write();
        assertFalse(service().report(null,null,null,null).comparable());
    }
}
