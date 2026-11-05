package com.example.invoice;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.service.DocumentAiResultPersistenceService;
import com.example.invoice.verification.FieldVerificationService;
import com.example.invoice.verification.FieldVerifier.AiInputMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:field_verification_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
@Import(com.example.invoice.support.IsolatedAccountFixtures.class)
class FieldVerificationIntegrationTest {
    @Autowired DocumentRepository documents; @Autowired InvoiceRepository invoices; @Autowired DocumentTypeRepository types;
    @Autowired CompanyRepository companies; @Autowired UserRepository users; @Autowired OCRResultRepository ocrResults;
    @Autowired FieldVerificationRepository verifications; @Autowired FieldVerificationService service;
    @Autowired DocumentAiResultPersistenceService persistence; @Autowired AiDocumentResultValidator validator;
    @Autowired EntityManager em; @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    Long documentId;

    @BeforeEach void setup() {
        assertTrue(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) c -> c.getMetaData().getURL().startsWith("jdbc:h2:mem:")));
        var company = new Company(); company.setCompanyName("Verification fixture"); companies.saveAndFlush(company);
        var type = types.findByCode("RETAIL_INVOICE").orElseGet(DocumentType::new);
        type.setCode("RETAIL_INVOICE"); type.setName("Hóa đơn bán lẻ"); type.setActive(true); types.saveAndFlush(type);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "test"));
        var document = new Document(); document.setCompany(company); document.setOriginalFileName("bill-" + UUID.randomUUID() + ".jpg");
        document.setFileType("image/jpeg"); document.setFileSize(1L); document.setStatus(DocumentStatus.PROCESSING);
        document.setUploadedBy(users.findByUsername("admin").orElseThrow()); documents.saveAndFlush(document);
        documentId = document.getId();
        var item = new AiDocumentResult.AiInvoiceItemExtraction("Giấy in A4", new BigDecimal("2"), "ram", new BigDecimal("55000"), null, null, new BigDecimal("110000"));
        var invoice = new AiDocumentResult.AiInvoiceExtraction(null, null, "2026-10-23", "Nhà sách Minh Khai", null, null, null, null, null,
                null, null, new BigDecimal("110000"), List.of(item));
        persistence.persist(documentId, validator.validate(new AiDocumentResult("unit", "unit", "RETAIL_INVOICE", new BigDecimal("0.99"),
                "Bill", invoice, List.of(), List.of("DOCUMENT_TYPE_REASON: Bill bán lẻ có bảng hàng và tổng tiền"), "{}", 1), Set.of("RETAIL_INVOICE")));
        var ocr = new OCRResult(); ocr.setDocument(document); ocr.setOcrEngine("tesseract"); ocr.setSourceType("TESS4J_WORD_LAYOUT");
        ocr.setRawText("NHÀ SÁCH MINH KHAI\nNgày 23/10/2026\nGiấy in A4  2  55.000  110.000\nTổng cộng: 110.000");
        ocr.setStatus("SUCCESS"); ocr.setProcessedAt(LocalDateTime.now()); ocrResults.saveAndFlush(ocr);
        em.flush(); em.clear();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    String token() throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "admin", "password", "Admin@123456")))).andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(response.getResponse().getContentAsString()).path("token").asText();
    }

    @Test void storesEvidenceRulesAndVersionAndNeverChangesValues() throws Exception {
        var before = invoices.findByDocumentId(documentId).orElseThrow();
        var total = before.getTotalAmount(); var seller = before.getSellerName();
        var report = service.verifyAfterProcessing(documentId, AiInputMode.TEXT);
        em.flush(); em.clear();

        var rows = verifications.findByDocumentIdOrderByIdAsc(documentId);
        assertFalse(rows.isEmpty());
        var totalRow = rows.stream().filter(r -> r.getFieldKey().equals("totalAmount")).findFirst().orElseThrow();
        assertEquals("VERIFIED", totalRow.getStatus());
        assertEquals("field-verification-v1", totalRow.getRuleVersion());
        assertTrue(totalRow.getRulesJson().contains("ITEMS_SUM_MATCHES"));
        assertTrue(totalRow.getEvidenceJson().contains("OCR_TEXT"));
        assertEquals("AI", totalRow.getValueOrigin());
        // RETAIL_INVOICE does not require a tax code, so none is reported missing.
        assertTrue(rows.stream().noneMatch(r -> r.getFieldKey().equals("sellerTaxCode")));
        assertEquals(0L, report.counts().get("MISSING"));
        var after = invoices.findByDocumentId(documentId).orElseThrow();
        assertEquals(0, total.compareTo(after.getTotalAmount())); assertEquals(seller, after.getSellerName());

        String auth = token();
        mvc.perform(get("/api/v1/documents/" + documentId + "/field-verifications").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.fields[?(@.fieldKey=='items[0].amount')].status").value("VERIFIED"))
                .andExpect(jsonPath("$.scoreNote").exists());
    }

    @Test void manualEditMakesResultStaleAndRecomputeMarksOrigin() throws Exception {
        service.verifyAfterProcessing(documentId, AiInputMode.TEXT);
        em.flush(); em.clear();
        String auth = token();
        Long invoiceId = invoices.findByDocumentId(documentId).orElseThrow().getId();
        mvc.perform(put("/api/v1/invoices/" + invoiceId + "/items").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productName\":\"Giấy in A4\",\"quantity\":2,\"unitPrice\":55000,\"amount\":120000}]}"))
                .andExpect(status().isOk());
        em.flush(); em.clear();
        mvc.perform(get("/api/v1/documents/" + documentId + "/field-verifications").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(true));
        mvc.perform(post("/api/v1/documents/" + documentId + "/field-verifications/recompute").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.aiInputMode").value("TEXT"))
                .andExpect(jsonPath("$.fields[?(@.fieldKey=='items[0].amount')].valueOrigin").value("MANUAL"))
                .andExpect(jsonPath("$.fields[?(@.fieldKey=='items[0].amount')].aiValue").value("110000"))
                .andExpect(jsonPath("$.fields[?(@.fieldKey=='items[0].amount')].status").value("WARNING"));
    }

    @Test void visionReadValueAbsentFromOcrIsUnverified() {
        var ocr = ocrResults.findFirstByDocumentIdOrderByProcessedAtDesc(documentId).orElseThrow();
        ocr.setRawText("ảnh mờ, OCR không đọc được tên cửa hàng"); ocrResults.saveAndFlush(ocr);
        var report = service.verifyAfterProcessing(documentId, AiInputMode.VISION);
        var seller = report.fields().stream().filter(f -> f.fieldKey().equals("sellerName")).findFirst().orElseThrow();
        assertEquals("UNVERIFIED", seller.status());
    }
}
