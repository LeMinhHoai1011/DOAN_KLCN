package com.example.invoice;

import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.invoice.RetailInvoiceDetails;
import com.example.invoice.dto.invoice.RetailInvoiceDetails.*;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:retail_invoice_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
@Import(com.example.invoice.support.IsolatedAccountFixtures.class)
class RetailInvoiceIntegrationTest {
    @Autowired DocumentRepository documents; @Autowired InvoiceRepository invoices;
    @Autowired DocumentTypeRepository types; @Autowired CompanyRepository companies;
    @Autowired UserRepository users; @Autowired ExtractedFieldRepository fields;
    @Autowired FinancialTransactionRepository payments; @Autowired DocumentAiResultPersistenceService persistence;
    @Autowired AiDocumentResultValidator validator; @Autowired EntityManager em;
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    Company company;
    @BeforeEach void setup() {
        assertTrue(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) c -> c.getMetaData().getURL().startsWith("jdbc:h2:mem:")));
        company=new Company(); company.setCompanyName("Retail isolated fixture"); companies.saveAndFlush(company);
        var accountant=users.findByUsername("accountant").orElseThrow(); accountant.setCompany(company); users.flush();
        var type=types.findByCode("RETAIL_INVOICE").orElseGet(DocumentType::new);
        type.setCode("RETAIL_INVOICE"); type.setName("Hóa đơn bán lẻ"); type.setActive(true); types.saveAndFlush(type);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    static BigDecimal n(String value) { return new BigDecimal(value); }
    static Adjustment absent() { return new Adjustment(null,Treatment.NOT_PRESENT); }
    RetailInvoiceDetails details() {
        return new RetailInvoiceDetails("14:30",LineAmountBasis.BEFORE_DISCOUNT,List.of(),absent(),absent(),absent(),n("200"),n("100"));
    }
    AiDocumentResult result(RetailInvoiceDetails details) {
        var item=new AiDocumentResult.AiInvoiceItemExtraction("Paper",n("2"),"pack",n("50"),null,null,n("100"));
        var invoice=new AiDocumentResult.AiInvoiceExtraction(null,null,"2026-10-23","Retail store",null,null,null,null,null,
                null,null,n("100"),List.of(item)).withRetailDetails(details);
        return new AiDocumentResult("unit","unit","RETAIL_INVOICE",n("0.99"),"Bill",invoice,List.of(),
                List.of("DOCUMENT_TYPE_REASON: Bill bán lẻ có bảng hàng và tổng tiền"),"{}",1);
    }
    Invoice persist(RetailInvoiceDetails details,Company owner) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","test"));
        var document=new Document(); document.setCompany(owner); document.setOriginalFileName("retail-"+UUID.randomUUID()+".jpg");
        document.setFileType("image/jpeg"); document.setFileSize(1L); document.setStatus(DocumentStatus.PROCESSING);
        document.setUploadedBy(users.findByUsername("admin").orElseThrow()); documents.saveAndFlush(document);
        persistence.persist(document.getId(),validator.validate(result(details),Set.of("RETAIL_INVOICE")));
        em.flush(); em.clear(); return invoices.findByDocumentId(document.getId()).orElseThrow();
    }
    String token(String username,String password) throws Exception {
        var response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",username,"password",password)))).andExpect(status().isOk()).andReturn();
        return "Bearer "+json.readTree(response.getResponse().getContentAsString()).path("token").asText();
    }
    @Test void aiRowsAndTypedMetadataSurviveReloadAndApprovalNeverCreatesCash() throws Exception {
        long count=payments.count(); Invoice invoice=persist(details(),company); Long id=invoice.getId(), doc=invoice.getDocument().getId();
        assertNull(invoice.getSellerTaxCode()); assertNull(invoice.getBuyerName()); assertNull(invoice.getVatAmount());
        assertEquals(1,invoice.getItems().size());
        assertEquals(DocumentStatus.PROCESSED,invoice.getDocument().getStatus());
        String auth=token("admin","Admin@123456");
        mvc.perform(get("/api/v1/invoices/"+id+"/retail-details").header("Authorization",auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.details.tax.treatment").value("NOT_PRESENT")).andExpect(jsonPath("$.stale").value(false));
        var checked=mvc.perform(post("/api/v1/documents/"+doc+"/reconciliations").header("Authorization",auth)).andExpect(status().isOk()).andReturn();
        assertTrue(json.readTree(checked.getResponse().getContentAsString()).findValuesAsText("status").stream().allMatch("MATCHED"::equals));
        mvc.perform(post("/api/v1/documents/"+doc+"/workflow").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"APPROVE\"}")).andExpect(status().isOk());
        assertEquals(count,payments.count());
        mvc.perform(put("/api/v1/invoices/"+id+"/retail-details").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(details()))).andExpect(status().isBadRequest());
    }
    @Test void correctionsInvalidateMetadataUntilReconfirmedAndManualEvidenceSurvivesAi() throws Exception {
        Invoice invoice=persist(details(),company); Long id=invoice.getId(), doc=invoice.getDocument().getId();
        String auth=token("admin","Admin@123456"), endpoint="/api/v1/invoices/"+id;
        mvc.perform(put(endpoint+"/items").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"productName\":\"User corrected paper\",\"quantity\":2,\"unitPrice\":50,\"amount\":100}]}"))
                .andExpect(status().isOk());
        em.flush(); em.clear();
        mvc.perform(get(endpoint+"/retail-details").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(true));
        mvc.perform(post("/api/v1/documents/"+doc+"/reconciliations").header("Authorization",auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("INSUFFICIENT_DATA"));
        mvc.perform(put(endpoint+"/retail-details").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(details()))).andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(false));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","test"));
        persistence.persist(doc,validator.validate(result(details()),Set.of("RETAIL_INVOICE"))); em.flush(); em.clear();
        assertEquals("User corrected paper",invoices.findById(id).orElseThrow().getItems().getFirst().getProductName());
        assertEquals("RETAIL_MANUAL",fields.findByDocumentId(doc).stream().filter(f -> RetailInvoiceDetailsService.FIELD.equals(f.getFieldName())).findFirst().orElseThrow().getSource());
        mvc.perform(get(endpoint+"/retail-details").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(false));
        var checked=mvc.perform(post("/api/v1/documents/"+doc+"/reconciliations").header("Authorization",auth)).andExpect(status().isOk()).andReturn();
        assertTrue(json.readTree(checked.getResponse().getContentAsString()).findValuesAsText("status").stream().allMatch("MATCHED"::equals));
    }
    @Test void invalidAndCrossCompanyOrEmployeeWritesAreRejected() throws Exception {
        Invoice own=persist(details(),company);
        Company other=new Company(); other.setCompanyName("Other isolated fixture"); companies.saveAndFlush(other);
        Invoice foreign=persist(details(),other);
        String accountant=token("accountant","Accountant@123");
        mvc.perform(get("/api/v1/invoices/"+foreign.getId()+"/retail-details").header("Authorization",accountant)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/invoices/"+foreign.getId()+"/retail-details").header("Authorization",accountant).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(details()))).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/invoices/"+own.getId()+"/retail-details").header("Authorization",token("employee","Employee@123"))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(details()))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/invoices/"+own.getId()+"/retail-details").header("Authorization",token("admin","Admin@123456"))
                .contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
    }
    @Test void missingRetailEvidenceStaysReviewableAndExportKeepsCashSeparateFromTotal() throws Exception {
        Invoice partial=persist(null,company);
        assertEquals(DocumentStatus.NEED_REVIEW,partial.getDocument().getStatus());
        Invoice invoice=persist(details(),company); String auth=token("admin","Admin@123456");
        var response=mvc.perform(get("/api/v1/exports/documents").header("Authorization",auth)
                .param("search",invoice.getDocument().getOriginalFileName())).andExpect(status().isOk()).andReturn();
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getResponse().getContentAsByteArray()))) {
            var row=book.getSheetAt(0).getRow(1); assertEquals(100,row.getCell(14).getNumericCellValue());
            assertEquals(200,row.getCell(22).getNumericCellValue()); assertEquals(100,row.getCell(23).getNumericCellValue());
            assertNull(row.getCell(18)); assertEquals("NOT_PRESENT",row.getCell(19).getStringCellValue());
        }
        assertTrue(fields.findByDocumentId(invoice.getDocument().getId()).stream().anyMatch(f -> RetailInvoiceDetailsService.FIELD.equals(f.getFieldName())));
        var extracted=mvc.perform(get("/api/v1/documents/"+invoice.getDocument().getId()+"/extracted-fields").header("Authorization",auth))
                .andExpect(status().isOk()).andReturn();
        assertFalse(extracted.getResponse().getContentAsString().contains(RetailInvoiceDetailsService.FIELD));
    }
}
