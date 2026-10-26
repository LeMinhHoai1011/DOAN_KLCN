package com.example.invoice.ai;

import com.example.invoice.config.AiProperties;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({DocumentAiResultPersistenceService.class, RetailInvoiceDetailsService.class, ProcessingLogService.class, DocumentAssessmentPersistenceTest.Config.class})
class DocumentAssessmentPersistenceTest {
    @TestConfiguration static class Config {
        @Bean AiProperties aiProperties() { return new AiProperties(); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
    @Autowired DocumentRepository documents;
    @Autowired DocumentTypeRepository types;
    @Autowired ExtractedFieldRepository fields;
    @Autowired FinancialTransactionRepository transactions;
    @Autowired InvoiceRepository invoices;
    @Autowired DocumentAiResultPersistenceService persistence;
    @Autowired ProcessingLogService logs;
    @MockitoBean DocumentService scopedDocuments;
    @MockitoBean DocumentAuditService documentAudit;

    @ParameterizedTest
    @ValueSource(strings = {"RECEIPT", "PAYMENT_VOUCHER", "RECEIPT_VOUCHER", "CONTRACT", "INVOICE"})
    void typeSpecificFieldsAndAssessmentPersistWithoutApprovalOrTransactions(String type) {
        DocumentType documentType = new DocumentType(); documentType.setCode(type); documentType.setName(type); types.saveAndFlush(documentType);
        Document document = new Document(); document.setOriginalFileName("test.png"); document.setFileType("image/png"); document.setFileSize(1L);
        document = documents.saveAndFlush(document);
        when(scopedDocuments.load(document.getId())).thenReturn(document);
        var validated = new AiDocumentResultValidator().validate(DocumentTypeValidationPolicyTest.result(type,
                DocumentTypeValidationPolicyTest.validFields(type)), java.util.Set.of(type));
        long before = transactions.count();
        assertFalse(persistence.persist(document.getId(), validated, null));
        documents.flush();
        assertEquals(DocumentStatus.PROCESSED, document.getStatus());
        assertEquals(ReviewStatus.PENDING, document.getReviewStatus());
        if (type.equals("INVOICE")) assertEquals(1, invoices.findByDocumentId(document.getId()).orElseThrow().getItems().size());
        else assertTrue(fields.findByDocumentId(document.getId()).stream().anyMatch(f -> f.getFieldName().equals(type.equals("CONTRACT") ? "subject" : "totalAmount")));
        assertEquals("VALID", logs.assessment(document.getId()).validationStatus());
        assertNull(logs.assessment(document.getId()).extractionConfidence().value());
        assertEquals(before, transactions.count());
    }
}
