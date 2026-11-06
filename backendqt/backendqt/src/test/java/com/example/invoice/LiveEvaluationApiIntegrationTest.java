package com.example.invoice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.invoice.entity.*;
import com.example.invoice.evaluation.LiveEvaluationData;
import com.example.invoice.evaluation.LiveEvaluationStore;
import com.example.invoice.repository.*;
import com.example.invoice.service.DocumentAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:live_evaluation_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(com.example.invoice.support.IsolatedAccountFixtures.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) @Transactional
class LiveEvaluationApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
    @Autowired DocumentRepository documents; @Autowired DocumentVersionRepository versions; @Autowired CompanyRepository companies;
    @Autowired AuditLogRepository audit; @Autowired UserRepository users;
    @MockitoBean LiveEvaluationStore store;
    Map<Long,LiveEvaluationData.Sample> stored=new HashMap<>();
    static final String PROPOSAL="""
        {"fileVersionId":%d,"ocr":{"rawText":"OCR"},"aiProposal":{"model":"m1","documentType":"VAT_INVOICE","durationMs":1000,
         "invoice":{"invoiceNumber":"AI-0001","totalAmount":"150000.00","items":[]}}}
        """;

    @BeforeAll void companies() {
        new TransactionTemplate(manager).executeWithoutResult(s->{
            for(long id:new long[]{1,13})jdbc.update("insert into companies(company_id,company_name) values (?,?)",id,"Isolated "+id);
            for(String name:List.of("accountant","employee"))users.findByUsername(name).orElseThrow().setCompany(companies.findById(13L).orElseThrow());
        });
    }
    @BeforeEach void store() {
        stored.clear();
        when(store.read(anyLong(),anyLong())).thenAnswer(i->Optional.ofNullable(stored.get(i.getArgument(1,Long.class))));
        doAnswer(i->{LiveEvaluationData.Sample s=i.getArgument(0);stored.put(s.getDocumentId(),s);return null;}).when(store).write(any());
        when(store.list(any())).thenAnswer(i->new ArrayList<>(stored.values()));
    }
    String token(String name)throws Exception {
        String password=name.equals("admin")?"Admin@123456":name.equals("employee")?"Employee@123":"Accountant@123";
        var result=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn();
        return "Bearer "+json.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
    Document document(long company)throws Exception {
        var d=new Document();d.setCompany(companies.findById(company).orElseThrow());d.setOriginalFileName("live.pdf");d.setFileType("application/pdf");d.setFileSize(1L);
        d.setStatus(DocumentStatus.NEED_REVIEW);d.setReviewStatus(ReviewStatus.PENDING);d.setDocumentType("VAT_INVOICE");d.setFilePath("key-"+UUID.randomUUID());
        documents.saveAndFlush(d);
        var v=new DocumentVersion();v.setDocument(d);v.setVersionNumber(1);v.setObjectKey(d.getFilePath());versions.saveAndFlush(v);
        var row=new AuditLog();row.setEntityType(DocumentAuditService.TYPE);row.setEntityId(d.getId().toString());row.setAction("AI_EXTRACT");row.setNewValue(PROPOSAL.formatted(v.getId()));
        audit.saveAndFlush(row);
        return d;
    }
    Map<String,Object> draft(int revision) {
        var fields=new LinkedHashMap<String,Object>();
        LiveEvaluationData.FIELDS.forEach(key->fields.put(key,Map.of("state","ABSENT")));
        fields.put("invoiceNumber",Map.of("state","PRESENT","value","0001"));fields.put("totalAmount",Map.of("state","PRESENT","value","180.000"));
        var body=new LinkedHashMap<String,Object>();
        body.put("revision",revision);body.put("documentType","VAT_INVOICE");body.put("fields",fields);body.put("category",Map.of("state","ABSENT"));body.put("itemsState","ABSENT");body.put("items",List.of());
        return body;
    }
    JsonNode ok(org.springframework.test.web.servlet.RequestBuilder request)throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String path(Document d) { return "/api/v1/evaluation-labels/"+d.getId(); }

    @Test void labellingKeepsPredictionBlindScoresIt_andWritesNothingToTheDatabase()throws Exception {
        var d=document(13);String auth=token("accountant");
        var counts=List.of("audit_logs","documents","document_versions","reconciliations");
        var before=counts.stream().map(t->jdbc.queryForObject("select count(*) from "+t,Long.class)).toList();
        ok(post(path(d)).header("Authorization",auth));
        var saved=ok(put(path(d)).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(draft(0))));
        assertFalse(saved.toString().contains("AI-0001"));assertTrue(saved.path("reveal").isNull());
        assertFalse(ok(get(path(d)).header("Authorization",auth)).toString().contains("150000"));
        var confirm=Map.of("revision",1,"reconciliation",json.convertValue(saved.path("reconciliationDraft"),Map.class));
        var done=ok(post(path(d)+"/confirm").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(confirm)));
        assertEquals("accountant",done.path("sample").path("confirmedBy").asText());
        var total=java.util.stream.StreamSupport.stream(done.path("reveal").path("fields").spliterator(),false).filter(c->c.path("field").asText().equals("totalAmount")).findFirst().orElseThrow();
        assertEquals("150000.00",total.path("predicted").asText());assertEquals("INCORRECT",total.path("outcome").asText());
        assertEquals(before,counts.stream().map(t->jdbc.queryForObject("select count(*) from "+t,Long.class)).toList());
        var report=ok(get("/api/v1/evaluation-labels/report").header("Authorization",token("admin")));
        assertEquals(1,report.path("scored").asInt());
        assertEquals(1,report.path("models").get(0).path("score").path("fields").path("totalAmount").path("incorrect").asInt());
    }

    @Test void accountantCannotReachAnotherCompanysSample()throws Exception {
        var foreign=document(1);String admin=token("admin"),accountant=token("accountant");
        ok(post(path(foreign)).header("Authorization",admin));
        mvc.perform(get(path(foreign)).header("Authorization",accountant)).andExpect(status().isNotFound());
        mvc.perform(post(path(foreign)).header("Authorization",accountant)).andExpect(status().isNotFound());
        assertEquals(0,ok(get("/api/v1/evaluation-labels").header("Authorization",accountant)).size());
        assertEquals(1,ok(get("/api/v1/evaluation-labels").header("Authorization",admin)).size());
    }

    @Test void onlyReviewersLabelAndOnlyAdminsReadTheReport()throws Exception {
        var d=document(13);
        mvc.perform(get("/api/v1/evaluation-labels")).andExpect(status().isUnauthorized());
        mvc.perform(post(path(d)).header("Authorization",token("employee"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/evaluation-labels/report").header("Authorization",token("accountant"))).andExpect(status().isForbidden());
    }

    @Test void conflictAndMissingSampleUseClearStatuses()throws Exception {
        var d=document(13);String auth=token("accountant");
        mvc.perform(get(path(d)).header("Authorization",auth)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("LIVE_SAMPLE_NOT_FOUND"));
        ok(post(path(d)).header("Authorization",auth));
        mvc.perform(put(path(d)).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(draft(7)))).andExpect(status().isConflict());
    }

    @Test void randomSelectionTakesOnlyEligibleDocumentsInScope()throws Exception {
        var own=document(13);document(1);
        var result=ok(post("/api/v1/evaluation-labels/random").header("Authorization",token("accountant")).contentType(MediaType.APPLICATION_JSON).content("{\"perType\":5}"));
        assertEquals(1,result.path("added").asInt());assertEquals(Set.of(own.getId()),stored.keySet());
        assertEquals(LiveEvaluationData.Selection.RANDOM,stored.get(own.getId()).getSelection());
    }
}
