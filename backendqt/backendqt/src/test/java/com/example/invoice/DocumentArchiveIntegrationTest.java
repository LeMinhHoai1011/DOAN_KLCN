package com.example.invoice;

import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.service.*;
import com.example.invoice.dto.document.*;
import com.example.invoice.dto.invoice.*;
import com.fasterxml.jackson.databind.*;
import io.minio.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:archive_v1_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(com.example.invoice.support.IsolatedAccountFixtures.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) @Transactional
class DocumentArchiveIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
    @Autowired DocumentService documents; @Autowired DocumentRepository documentRows; @Autowired DocumentVersionRepository versions;
    @Autowired DocumentArchiveService archive; @Autowired AuditLogRepository audit; @Autowired InvoiceRepository invoices;
    @Autowired InvoiceService invoiceService; @Autowired OCRResultService ocr; @Autowired UserRepository users; @Autowired CompanyRepository companies;
    @Autowired DocumentAiResultPersistenceService persistence; @Autowired DocumentTypeRepository types;
    @MockitoBean MinioClient minio;
    @BeforeAll void seed(){assertTrue(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)c->c.getMetaData().getURL().startsWith("jdbc:h2:mem:archive_v1_test")));
        new TransactionTemplate(manager).executeWithoutResult(tx->{for(long id:new long[]{1,13})jdbc.update("insert into companies(company_id,company_name) values (?,?)",id,"Archive isolated "+id);for(String name:List.of("admin","accountant","employee")){users.findByUsername(name).orElseThrow().setCompany(companies.findById(13L).orElseThrow());}var type=new DocumentType();type.setCode("INVOICE");type.setName("Invoice");types.saveAndFlush(type);});}
    @BeforeEach void actor(){clearInvocations(minio);SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("accountant","unused","ROLE_ACCOUNTANT","PERMISSION_DOCUMENT_VIEW","PERMISSION_DOCUMENT_DOWNLOAD"));}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    MockMultipartFile file(String name)throws Exception {var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("file",name,"image/png",out.toByteArray());}
    Long upload()throws Exception{return documents.createFromUpload(file("old.png"),SecurityContextHolder.getContext().getAuthentication()).id();}
    String token(String name)throws Exception{String password=name.equals("admin")?"Admin@123456":name.equals("employee")?"Employee@123":"Accountant@123";var r=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",name,"password",password)))).andExpect(status().isOk()).andReturn();return "Bearer "+json.readTree(r.getResponse().getContentAsString()).path("token").asText();}
    String snapshots(Long id){try{return json.writeValueAsString(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,id.toString()).stream().map(AuditLog::getNewValue).toList());}catch(Exception e){throw new RuntimeException(e);}}

    @Test void uploadsKeepOriginalAndMetadataChecksumAndActor()throws Exception {
        Long id=upload();String originalKey=documentRows.findById(id).orElseThrow().getFilePath();var original=versions.findByDocumentId(id).getFirst();
        documents.uploadNewVersion(id,file("new.png"),SecurityContextHolder.getContext().getAuthentication());
        var all=archive.versions(id);assertEquals(2,all.size());assertEquals(2,all.getFirst().number());assertTrue(all.getFirst().current());assertTrue(all.getLast().original());
        assertEquals(originalKey,versions.findById(original.getId()).orElseThrow().getObjectKey());assertNotEquals(originalKey,documentRows.findById(id).orElseThrow().getFilePath());
        var event=audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,id.toString()).getFirst();assertEquals("FILE_VERSION_UPLOAD",event.getAction());assertEquals(users.findByUsername("accountant").orElseThrow().getId(),event.getUser().getId());assertNotNull(event.getOldValue());assertNotNull(event.getNewValue());
        var arguments=org.mockito.ArgumentCaptor.forClass(PutObjectArgs.class);verify(minio,times(2)).putObject(arguments.capture());for(var args:arguments.getAllValues())assertTrue(args.userMetadata().values().stream().anyMatch(v->v.matches("[a-f0-9]{64}")));
        verify(minio,never()).removeObject(any());assertEquals(DocumentStatus.UPLOADED,documentRows.findById(id).orElseThrow().getStatus());
    }
    @Test void metadataCannotSwitchObjectOrClaimExistingObjectAndDeletionIsBlocked()throws Exception {
        Long id=upload();String key=documentRows.findById(id).orElseThrow().getFilePath();String auth=token("accountant");
        mvc.perform(put("/api/v1/documents/"+id).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"filePath\":\"company-1/private.png\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/documents").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("originalFileName","alias.png","fileType","image/png","fileSize",10,"filePath",key)))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/documents/"+id).header("Authorization",token("admin"))).andExpect(status().isBadRequest());
        assertEquals(key,documentRows.findById(id).orElseThrow().getFilePath());assertEquals(1,versions.findByDocumentId(id).size());verify(minio,never()).removeObject(any());
    }
    @Test void wrongCompanyAndWrongDocumentVersionAreDeniedBeforeMinio()throws Exception {
        Long own=upload(),other=upload();var foreign=documentRows.findById(other).orElseThrow();foreign.setCompany(companies.findById(1L).orElseThrow());documentRows.flush();
        Long foreignVersion=versions.findByDocumentId(other).getFirst().getId();String auth=token("accountant");clearInvocations(minio);
        for(String suffix:List.of("storage","versions","history"))mvc.perform(get("/api/v1/documents/"+other+"/"+suffix).header("Authorization",auth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/"+own+"/versions/"+foreignVersion+"/preview").header("Authorization",auth)).andExpect(status().isNotFound());
        verifyNoInteractions(minio);
    }
    @Test void viewPermissionDoesNotImplicitlyGrantDownloadAndEmployeeIsScopedToUploader()throws Exception {
        Long id=upload(),version=versions.findByDocumentId(id).getFirst().getId();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("accountant","unused","ROLE_ACCOUNTANT","PERMISSION_DOCUMENT_VIEW"));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,()->archive.download(id,version));
        mvc.perform(get("/api/v1/documents/"+id+"/versions").header("Authorization",token("employee"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/documents/"+id+"/versions")).andExpect(status().isUnauthorized());
    }
    @Test void historicalFileDownloadsUseVersionKeyNotCurrentKeyAndReadDoesNotWrite()throws Exception {
        Long id=upload();var old=versions.findByDocumentId(id).getFirst();documents.uploadNewVersion(id,file("second.png"),SecurityContextHolder.getContext().getAuthentication());
        long count=audit.count();var stream=mock(GetObjectResponse.class);when(stream.read(any(byte[].class),anyInt(),anyInt())).thenReturn(-1);when(minio.getObject(any())).thenReturn(stream);
        String auth=token("accountant");mvc.perform(get("/api/v1/documents/"+id+"/versions/"+old.getId()+"/download").header("Authorization",auth)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        var captured=org.mockito.ArgumentCaptor.forClass(GetObjectArgs.class);verify(minio).getObject(captured.capture());assertEquals(old.getObjectKey(),captured.getValue().object());assertEquals(count,audit.count());
    }
    @Test void storageAvailabilityChecksumAndObjectKeysAreSafelyProjected()throws Exception {
        Long id=upload();var stat=mock(StatObjectResponse.class);when(stat.size()).thenReturn(documentRows.findById(id).orElseThrow().getFileSize());when(stat.contentType()).thenReturn("image/png");when(stat.etag()).thenReturn("opaque-etag");when(stat.userMetadata()).thenReturn(Map.of());when(minio.statObject(any())).thenReturn(stat);
        var info=archive.storage(id);assertEquals("AVAILABLE",info.status());assertTrue(info.metadataMatches());assertNull(info.checksum());assertEquals("opaque-etag",info.etag());
        var response=json.writeValueAsString(info);assertFalse(response.contains("objectKey"));assertFalse(response.contains(documentRows.findById(id).orElseThrow().getFilePath()));
        when(minio.statObject(any())).thenThrow(new RuntimeException("secret-token bucket-private host-private"));assertEquals("UNAVAILABLE",archive.storage(id).status());assertFalse(json.writeValueAsString(archive.storage(id)).contains("secret-token"));
    }
    InvoiceRequest request(Long id,String number,int amount){return new InvoiceRequest(id,number,LocalDate.of(2026,10,17),"Seller",null,null,"Buyer",null,null,BigDecimal.valueOf(amount),BigDecimal.ZERO,BigDecimal.valueOf(amount),List.of());}
    @Test void invoiceAndOcrCorrectionsPreserveBeforeAfterAndForeignSnapshotIsDenied()throws Exception {
        Long id=upload();var inv=invoiceService.create(request(id,"OLD",100));invoiceService.update(inv.id(),request(id,"NEW",120));
        ocr.upsert(id,new OCRResultRequest("Văn bản cũ",null));ocr.upsert(id,new OCRResultRequest("Văn bản mới",null));
        var events=audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,id.toString());
        var edit=events.stream().filter(e->e.getAction().equals("INVOICE_UPDATE")).findFirst().orElseThrow();assertEquals("OLD",json.readTree(edit.getOldValue()).path("invoice").path("invoiceNumber").asText());assertEquals("NEW",json.readTree(edit.getNewValue()).path("invoice").path("invoiceNumber").asText());
        var text=events.stream().filter(e->e.getAction().equals("OCR_EDIT")&&e.getOldValue().contains("Văn bản cũ")).findFirst().orElseThrow();assertTrue(text.getNewValue().contains("Văn bản mới"));
        long count=audit.count();archive.history(id);archive.snapshot(id,edit.getId(),"SNAPSHOT");assertEquals(count,audit.count());
        Long other=upload();assertThrows(com.example.invoice.exception.ResourceNotFoundException.class,()->archive.snapshot(other,edit.getId(),"SNAPSHOT"));
    }
    @Test void aiReprocessKeepsPreviousResultSnapshotAndConfirmedInvoice()throws Exception {
        Long id=upload();for(String number:List.of("AI-OLD","AI-NEW")){
            var result=json.readValue("{\"provider\":\"test\",\"model\":\"unit\",\"documentType\":\"INVOICE\",\"classificationConfidence\":0.99,\"invoice\":{\"invoiceNumber\":\""+number+"\",\"totalAmount\":100,\"items\":[]},\"fields\":[],\"extraFields\":[],\"warnings\":[]}",com.example.invoice.dto.ai.AiDocumentResult.class);
            persistence.persist(id,new com.example.invoice.ai.AiDocumentResultValidator().validate(result,Set.of("INVOICE")));
        }
        var ai=audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,id.toString()).stream().filter(e->e.getAction().equals("AI_EXTRACT")).toList();assertEquals(2,ai.size());assertTrue(ai.getFirst().getOldValue().contains("AI-OLD"));assertTrue(ai.getFirst().getNewValue().contains("AI-NEW"));
        var inv=invoices.findByDocumentId(id).orElseThrow();invoiceService.update(inv.getId(),request(id,"HUMAN",150));
        var result=json.readValue("{\"provider\":\"test\",\"model\":\"unit\",\"documentType\":\"INVOICE\",\"invoice\":{\"invoiceNumber\":\"BAD-OVERWRITE\",\"totalAmount\":1,\"items\":[]},\"fields\":[],\"warnings\":[]}",com.example.invoice.dto.ai.AiDocumentResult.class);
        persistence.persist(id,new com.example.invoice.ai.AiDocumentResultValidator().validate(result,Set.of("INVOICE")));assertEquals("HUMAN",invoices.findByDocumentId(id).orElseThrow().getInvoiceNumber());
        var latest=audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,id.toString()).getFirst();var saved=json.readTree(latest.getNewValue());
        assertEquals("BAD-OVERWRITE",saved.path("aiProposal").path("invoice").path("invoiceNumber").asText());assertEquals("HUMAN",saved.path("invoice").path("invoiceNumber").asText());assertFalse(saved.path("aiProposal").has("rawResponse"));
    }
    @Test void approvedDocumentCannotChangeFileOrDataAndHistoryIsReadOnly()throws Exception {
        Long id=upload();var d=documentRows.findById(id).orElseThrow();d.setStatus(DocumentStatus.COMPLETED);d.setReviewStatus(ReviewStatus.APPROVED);documentRows.flush();long count=audit.count();
        assertThrows(com.example.invoice.exception.BadRequestException.class,()->documents.uploadNewVersion(id,file("blocked.png"),SecurityContextHolder.getContext().getAuthentication()));
        assertThrows(com.example.invoice.exception.BadRequestException.class,()->ocr.upsert(id,new OCRResultRequest("blocked",null)));
        assertEquals(count,audit.count());assertEquals(1,archive.versions(id).size());assertFalse(Arrays.stream(com.example.invoice.controller.DocumentArchiveController.class.getMethods()).anyMatch(m->m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)||m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)||m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)));
    }
    @Test void legacyFileWithoutVersionsCannotBeSilentlyRelabelledAsOriginal()throws Exception {
        var d=new Document();d.setCompany(companies.findById(13L).orElseThrow());d.setOriginalFileName("legacy.png");d.setFileType("image/png");d.setFileSize(10L);d.setFilePath("company-13/legacy-private-key.png");d=documentRows.saveAndFlush(d);Long id=d.getId();
        assertNull(archive.storage(id).original());clearInvocations(minio);
        assertThrows(com.example.invoice.exception.BadRequestException.class,()->documents.uploadNewVersion(id,file("new.png"),SecurityContextHolder.getContext().getAuthentication()));
        assertTrue(versions.findByDocumentId(id).isEmpty());verifyNoInteractions(minio);assertEquals("company-13/legacy-private-key.png",documentRows.findById(id).orElseThrow().getFilePath());
    }
    @Test void ordinaryRequestsCannotUpdateOrDeleteHistory()throws Exception {
        Long id=upload();var e=audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,id.toString()).getFirst();long count=audit.count();String auth=token("admin");
        mvc.perform(put("/api/v1/documents/"+id+"/history/"+e.getId()).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/v1/documents/"+id+"/history/"+e.getId()).header("Authorization",auth)).andExpect(status().isMethodNotAllowed());assertEquals(count,audit.count());
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void rollbackLeavesNeitherBusinessEditNorSnapshot()throws Exception {
        Long id=upload();String original=documentRows.findById(id).orElseThrow().getOriginalFileName();long count=audit.count();
        var template=new TransactionTemplate(manager);template.executeWithoutResult(tx->{documents.update(id,new DocumentUpdateRequest("rolled-back.png",null,null,null,null,null),SecurityContextHolder.getContext().getAuthentication());tx.setRollbackOnly();});
        // Independent transaction rolls back both the edit and its appended snapshot.
        entityManager.clear();assertEquals(original,documentRows.findById(id).orElseThrow().getOriginalFileName());assertEquals(count,audit.count());
    }
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @Autowired ExtractedFieldRepository extractedFields; @Autowired FieldCorrectionRepository fieldCorrections; @Autowired ExtractedFieldCorrectionService correctionService;
    @Test void correctedFieldAndItsOldNewValuesSurviveAiReplacement()throws Exception {
        Long id=upload();var d=documentRows.findById(id).orElseThrow();d.setStatus(DocumentStatus.NEED_REVIEW);
        var f=new ExtractedField();f.setDocument(d);f.setFieldName("deliveryAddress");f.setFieldValue("AI-OLD");f.setSource("AI");f=extractedFields.saveAndFlush(f);
        correctionService.correct(id,f.getId(),new ExtractedFieldCorrectionRequest("HUMAN-CONFIRMED"),SecurityContextHolder.getContext().getAuthentication());
        var correction=fieldCorrections.findFirstByFieldIdOrderByCreatedAtDescIdDesc(f.getId()).orElseThrow();
        var result=json.readValue("{\"provider\":\"test\",\"model\":\"unit\",\"documentType\":\"INVOICE\",\"fields\":[{\"fieldName\":\"deliveryAddress\",\"fieldValue\":\"AI-NEW\"}],\"warnings\":[]}",com.example.invoice.dto.ai.AiDocumentResult.class);
        persistence.persist(id,new com.example.invoice.ai.AiDocumentResultValidator().validate(result,Set.of("INVOICE")));
        assertTrue(extractedFields.existsById(f.getId()));assertEquals("HUMAN-CONFIRMED",fieldCorrections.findById(correction.getId()).orElseThrow().getNewValue());
        var history=archive.snapshot(id,correction.getId(),"FIELD_CORRECTION");assertEquals("AI-OLD",history.before().path("deliveryAddress").asText());assertEquals("HUMAN-CONFIRMED",history.after().path("deliveryAddress").asText());
    }
    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentFileVersionsAreSerializedWithoutReplacingOriginal()throws Exception {
        Long id=upload();Long original=versions.findByDocumentId(id).getFirst().getId();var first=file("first.png");var second=file("second.png");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var barrier=new java.util.concurrent.CyclicBarrier(2);
        try {
            java.util.concurrent.Callable<Integer> a=()->{SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("accountant","unused","ROLE_ACCOUNTANT","PERMISSION_DOCUMENT_VIEW","PERMISSION_DOCUMENT_DOWNLOAD"));try{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);documents.uploadNewVersion(id,first,SecurityContextHolder.getContext().getAuthentication());return 1;}finally{SecurityContextHolder.clearContext();}};
            java.util.concurrent.Callable<Integer> b=()->{SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("accountant","unused","ROLE_ACCOUNTANT","PERMISSION_DOCUMENT_VIEW","PERMISSION_DOCUMENT_DOWNLOAD"));try{barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);documents.uploadNewVersion(id,second,SecurityContextHolder.getContext().getAuthentication());return 1;}finally{SecurityContextHolder.clearContext();}};
            var x=pool.submit(a);var y=pool.submit(b);assertEquals(1,x.get(20,java.util.concurrent.TimeUnit.SECONDS));assertEquals(1,y.get(20,java.util.concurrent.TimeUnit.SECONDS));
        }finally{pool.shutdownNow();}
        assertEquals(Set.of(1,2,3),versions.findByDocumentId(id).stream().map(DocumentVersion::getVersionNumber).collect(java.util.stream.Collectors.toSet()));assertTrue(versions.existsById(original));
    }
    @Test void financialDecimalSnapshotsRemainExactBeyondJavaScriptNumberPrecision()throws Exception {
        Long id=upload();var amount=new BigDecimal("9007199254740992.01");
        invoiceService.create(new InvoiceRequest(id,"EXACT",LocalDate.of(2026,10,17),"Seller",null,null,"Buyer",null,null,amount,BigDecimal.ZERO,amount,List.of()));
        var e=audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,id.toString()).getFirst();
        var value=archive.snapshot(id,e.getId(),"SNAPSHOT").after().path("invoice").path("totalAmount");assertTrue(value.isTextual());assertEquals("9007199254740992.01",value.asText());
    }
}
