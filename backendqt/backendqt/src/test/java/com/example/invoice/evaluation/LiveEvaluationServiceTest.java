package com.example.invoice.evaluation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.invoice.entity.*;
import com.example.invoice.evaluation.LiveEvaluationData.Label;
import com.example.invoice.evaluation.LiveEvaluationData.Sample;
import com.example.invoice.evaluation.LiveEvaluationData.State;
import com.example.invoice.evaluation.LiveEvaluationData.Status;
import com.example.invoice.evaluation.LiveEvaluationService.*;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.AuditLogRepository;
import com.example.invoice.repository.DocumentVersionRepository;
import com.example.invoice.service.DocumentAuditService;
import com.example.invoice.service.DocumentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class LiveEvaluationServiceTest {
    DocumentService documents=mock(DocumentService.class);
    DocumentVersionRepository versions=mock(DocumentVersionRepository.class);
    LiveEvaluationStore store=mock(LiveEvaluationStore.class);
    AuditLogRepository audit=mock(AuditLogRepository.class);
    ObjectMapper json=new ObjectMapper();
    LiveEvaluationService service=new LiveEvaluationService(documents,versions,store,new LivePrediction.Reader(audit,json));
    Authentication user=new UsernamePasswordAuthenticationToken("reviewer","x");
    Map<Long,Sample> stored=new HashMap<>();
    Document document;
    DocumentVersion version;

    AuditLog row(long id,String action,String body) {
        var log=new AuditLog();log.setId(id);log.setAction(action);log.setNewValue(body);log.setCreatedAt(LocalDateTime.now());return log;
    }
    static final String SNAPSHOT="""
        {"fileVersionId":3,"ocr":{"rawText":"OCR goc"},"invoice":{"totalAmount":"999"},
         "aiProposal":{"model":"m1","documentType":"VAT_INVOICE","accountingCategoryCode":"OFFICE","durationMs":1200,
          "invoice":{"invoiceNumber":"0001","totalAmount":"108000.00","items":[{"productName":"Giay","quantity":2,"unitPrice":"54000.00","amount":"108000.00"}]}}}
        """;

    @BeforeEach void setup() {
        document=new Document();document.setId(5L);document.setDocumentType("VAT_INVOICE");document.setFilePath("key");document.setOriginalFileName("a.pdf");document.setStatus(DocumentStatus.NEED_REVIEW);
        var company=new Company();company.setId(1L);document.setCompany(company);
        version=new DocumentVersion();version.setId(3L);version.setObjectKey("key");version.setVersionNumber(1);
        when(documents.load(5L)).thenReturn(document);
        when(versions.findByDocumentId(5L)).thenReturn(List.of(version));
        when(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,"5")).thenReturn(List.of(row(8,"WORKFLOW_APPROVE","{}"),row(7,"AI_EXTRACT",SNAPSHOT)));
        when(store.read(eq(1L),anyLong())).thenAnswer(i->Optional.ofNullable(stored.get(i.getArgument(1,Long.class))));
        doAnswer(i->{Sample s=i.getArgument(0);stored.put(s.getDocumentId(),s);return null;}).when(store).write(any());
        when(store.list(any())).thenAnswer(i->new ArrayList<>(stored.values()));
    }

    DraftRequest complete(int revision) {
        var fields=new LinkedHashMap<String,Label>();
        LiveEvaluationData.FIELDS.forEach(key->fields.put(key,new Label(State.ABSENT,null)));
        fields.put("invoiceNumber",new Label(State.PRESENT,"0001"));
        fields.put("subtotal",new Label(State.PRESENT,"100.000"));fields.put("vatAmount",new Label(State.PRESENT,"8.000"));fields.put("totalAmount",new Label(State.PRESENT,"108.000"));
        return new DraftRequest(revision,"VAT_INVOICE",fields,new Label(State.PRESENT,"OFFICE"),State.PRESENT,
                List.of(LiveEvaluationScoringTest.row("Giay","2","50.000","100.000","8%","8.000")),null);
    }
    Workspace confirmed() {
        service.select(5L,user);
        var saved=service.save(5L,complete(0));
        return service.confirm(5L,new ConfirmRequest(saved.sample().getRevision(),saved.reconciliationDraft()),user);
    }

    @Test void selectionPinsTheAiRunAndStartsUnchecked() {
        var summary=service.select(5L,user);
        assertEquals(Status.SELECTED,summary.status());
        var sample=stored.get(5L);assertEquals(7L,sample.getSnapshotId());assertEquals(3L,sample.getFileVersionId());
        assertEquals(LiveEvaluationData.FIELDS.size(),sample.getFields().size());assertEquals(State.UNCHECKED,sample.getFields().get("totalAmount").state());
    }

    @Test void selectingAgainKeepsExistingWork() {
        service.select(5L,user);service.save(5L,complete(0));
        assertEquals(Status.DRAFT,service.select(5L,user).status());
        assertEquals(1,stored.get(5L).getRevision());
    }

    @Test void documentsWithoutUntouchedAiOutputCannotBeSelected() {
        when(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc(DocumentAuditService.TYPE,"5")).thenReturn(List.of(row(7,"AI_EXTRACT","{\"fileVersionId\":3}")));
        assertTrue(assertThrows(BadRequestException.class,()->service.select(5L,user)).getMessage().startsWith("AI_SNAPSHOT_REQUIRED"));
        assertTrue(stored.isEmpty());
    }

    @Test void runOnAnotherFileVersionIsNotUsed() {
        version.setId(4L);
        assertThrows(BadRequestException.class,()->service.select(5L,user));
    }

    @Test void nonInvoiceDocumentsAreRejected() {
        document.setDocumentType("RECEIPT");
        assertThrows(BadRequestException.class,()->service.select(5L,user));
    }

    @Test void workspaceNeverRevealsAiValuesBeforeConfirmation()throws Exception {
        service.select(5L,user);
        var saved=service.save(5L,complete(0));
        assertNull(saved.reveal());
        assertFalse(json.findAndRegisterModules().writeValueAsString(service.workspace(5L)).contains("54000"));
    }

    @Test void businessEditsNeverChangeThePrediction() {
        var workspace=confirmed();
        assertEquals("108000.00",workspace.reveal().fields().stream().filter(c->c.field().equals("totalAmount")).findFirst().orElseThrow().predicted());
        assertEquals("m1",workspace.reveal().model());
    }

    @Test void revealMarksEachOutcome() {
        var fields=confirmed().reveal().fields().stream().collect(java.util.stream.Collectors.toMap(Comparison::field,Comparison::outcome));
        assertEquals("CORRECT",fields.get("invoiceNumber"));assertEquals("CORRECT",fields.get("totalAmount"));
        assertEquals("MISSING",fields.get("subtotal"));assertEquals("NOT_APPLICABLE",fields.get("sellerName"));
    }

    @Test void uncheckedOrEmptyValuesBlockConfirmation() {
        service.select(5L,user);
        var request=complete(0);request.fields().put("sellerName",Label.unchecked());
        var saved=service.save(5L,request);
        assertThrows(BadRequestException.class,()->service.confirm(5L,new ConfirmRequest(1,saved.reconciliationDraft()),user));
        request=complete(1);request.fields().put("sellerName",new Label(State.PRESENT," "));
        var again=service.save(5L,request);
        assertThrows(BadRequestException.class,()->service.confirm(5L,new ConfirmRequest(2,again.reconciliationDraft()),user));
        assertEquals(Status.DRAFT,stored.get(5L).getStatus());
    }

    @Test void reconciliationMustBeReviewedAndMayBeCorrected() {
        service.select(5L,user);
        var saved=service.save(5L,complete(0));
        assertThrows(BadRequestException.class,()->service.confirm(5L,new ConfirmRequest(1,null),user));
        var corrected=new LinkedHashMap<>(saved.reconciliationDraft());corrected.put("TOTAL","MISMATCH");
        var done=service.confirm(5L,new ConfirmRequest(1,corrected),user);
        assertEquals("MISMATCH",done.sample().getReconciliation().get("TOTAL"));
        assertEquals("reviewer",done.sample().getConfirmedBy());
    }

    @Test void staleRevisionIsRejected() {
        service.select(5L,user);service.save(5L,complete(0));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.save(5L,complete(0))).getStatusCode().value());
    }

    @Test void changedFileBlocksConfirmationAndScoring() {
        service.select(5L,user);
        var saved=service.save(5L,complete(0));
        document.setFilePath("other");
        assertTrue(service.workspace(5L).stale());
        assertThrows(BadRequestException.class,()->service.confirm(5L,new ConfirmRequest(1,saved.reconciliationDraft()),user));
        document.setFilePath("key");confirmedAfterSave(saved);
        document.setFilePath("other");
        var report=service.report(null);
        assertEquals(1,report.stale());assertEquals(0,report.scored());assertTrue(report.models().isEmpty());
    }
    void confirmedAfterSave(Workspace saved) { service.confirm(5L,new ConfirmRequest(saved.sample().getRevision(),saved.reconciliationDraft()),user); }

    @Test void reopeningAfterRevealIsRecorded() {
        var done=confirmed();
        assertThrows(BadRequestException.class,()->service.save(5L,complete(done.sample().getRevision())));
        var open=service.reopen(5L,done.sample().getRevision());
        assertEquals(Status.DRAFT,open.sample().getStatus());assertTrue(open.sample().isRevisedAfterReveal());assertNull(open.reveal());
        assertEquals(1,service.select(5L,user).revisedAfterReveal()?1:0);
    }

    @Test void ocrDraftComesFromTheRunAndIsRecordedAsAssisted() {
        service.select(5L,user);
        assertEquals("OCR goc",service.ocrDraft(5L));
        assertTrue(stored.get(5L).isOcrAssisted());
    }

    @Test void reportScoresOnlyConfirmedSamplesPerModel() {
        service.select(5L,user);
        assertEquals(0,service.report(null).scored());assertEquals(1200d,service.report(null).averageAiMs());
        var saved=service.save(5L,complete(0));confirmedAfterSave(saved);
        var report=service.report(null);
        assertEquals(1,report.confirmed());assertEquals(1,report.scored());assertEquals(1,report.manualSelected());
        var model=report.models().getFirst();assertEquals("m1",model.model());
        assertEquals(1,model.score().fields().get("subtotal").missing());
        assertEquals(1,model.score().classificationAccuracy());
    }

    @Test void perTypeLimitIsValidated() {
        assertThrows(BadRequestException.class,()->service.selectRandom(new RandomRequest(null,null,null,0),user));
        assertThrows(BadRequestException.class,()->service.selectRandom(new RandomRequest(null,null,null,51),user));
    }
}
