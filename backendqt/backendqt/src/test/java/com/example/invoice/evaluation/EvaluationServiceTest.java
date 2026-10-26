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
        String model="qwen3-vl",digest="a".repeat(64),env="test-only fixed CPU RAM GPU threads seed warm-up",revision="test-revision";
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
}
