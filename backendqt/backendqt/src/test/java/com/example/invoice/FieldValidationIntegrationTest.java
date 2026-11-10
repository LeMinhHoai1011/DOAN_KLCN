package com.example.invoice;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.invoice.ai.AiDocumentResultParser;
import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.ai.FieldValidationReport;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.service.DocumentAiResultPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Parser → validator → persistence → API, on H2, for the per-field report and manual-edit preservation. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:field_validation_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
@Import(com.example.invoice.support.IsolatedAccountFixtures.class)
class FieldValidationIntegrationTest {
	@Autowired DocumentRepository documents; @Autowired InvoiceRepository invoices;
	@Autowired DocumentTypeRepository types; @Autowired CompanyRepository companies;
	@Autowired UserRepository users; @Autowired ExtractedFieldRepository fields;
	@Autowired DocumentAiResultPersistenceService persistence; @Autowired AiDocumentResultValidator validator;
	@Autowired EntityManager em; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
	Company company;

	static final String KCT_INVOICE = """
		{"documentType":"VAT_INVOICE","classificationConfidence":0.95,"invoice":{"invoiceNumber":"0000123","invoiceSeries":"1C25TAA",
		"invoiceDate":"15/03/2025","sellerName":"Công ty Sách A","sellerTaxCode":"0100109106","buyerName":"Công ty B",
		"subtotal":"200.000","vatAmount":null,"totalAmount":"200.000","items":[
		{"productName":"Sách giáo khoa","quantity":"2","unit":"Cuốn","unitPrice":"50.000","taxRate":"KCT","amount":"100.000"},
		{"productName":"Sách giáo khoa","quantity":"2","unit":"Cuốn","unitPrice":"50.000","taxRate":"KCT","amount":"100.000"}]},
		"warnings":["DOCUMENT_TYPE_REASON: có ký hiệu, số hóa đơn và MST người bán"]}
		""";

	@BeforeEach void setup() {
		company = new Company(); company.setCompanyName("Field validation fixture"); companies.saveAndFlush(company);
		var type = types.findByCode("VAT_INVOICE").orElseGet(DocumentType::new);
		type.setCode("VAT_INVOICE"); if (type.getName() == null) type.setName("Hóa đơn GTGT"); type.setActive(true); types.saveAndFlush(type);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "test"));
	}
	@AfterEach void clear() { SecurityContextHolder.clearContext(); }

	Document newDocument() {
		var document = new Document(); document.setCompany(company); document.setOriginalFileName("vat-" + UUID.randomUUID() + ".pdf");
		document.setFileType("application/pdf"); document.setFileSize(1L); document.setStatus(DocumentStatus.PROCESSING);
		document.setUploadedBy(users.findByUsername("admin").orElseThrow()); documents.saveAndFlush(document);
		return document;
	}

	void persist(Long documentId, String modelJson) {
		AiDocumentResult parsed = AiDocumentResultParser.parse("unit", "unit", modelJson, "{}", 1L);
		persistence.persist(documentId, validator.validate(parsed, Set.of("VAT_INVOICE")));
		em.flush(); em.clear();
	}

	JsonNode report(Long documentId) throws Exception {
		var stored = fields.findByDocumentId(documentId).stream()
				.filter(f -> FieldValidationReport.FIELD.equals(f.getFieldName())).toList();
		assertEquals(1, stored.size(), "exactly one report row per document");
		assertEquals(FieldValidationReport.SOURCE, stored.getFirst().getSource());
		return json.readTree(stored.getFirst().getFieldValue());
	}

	String token() throws Exception {
		var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json.writeValueAsString(Map.of("username", "admin", "password", "Admin@123456")))).andExpect(status().isOk()).andReturn();
		return "Bearer " + json.readTree(response.getResponse().getContentAsString()).path("token").asText();
	}

	@Test void storesRawNormalizedAndStatusPerFieldAndKeepsBothIdenticalRows() throws Exception {
		Long id = newDocument().getId();
		persist(id, KCT_INVOICE);

		Invoice invoice = invoices.findByDocumentId(id).orElseThrow();
		assertEquals(2, invoice.getItems().size(), "two identical printed rows are two rows");
		assertNull(invoice.getItems().getFirst().getTaxRate(), "KCT is stored as no rate, never as 0");
		assertEquals(0, new BigDecimal("50000").compareTo(invoice.getItems().getFirst().getUnitPrice()));
		assertEquals(java.time.LocalDate.of(2025, 3, 15), invoice.getInvoiceDate());

		JsonNode report = report(id);
		assertTrue(report.path("applied").asBoolean());
		JsonNode date = find(report.path("fields"), "invoiceDate", null);
		assertEquals("15/03/2025", date.path("rawValue").asText());
		assertEquals("2025-03-15", date.path("normalizedValue").asText());
		assertEquals("PRESENT_NORMALIZED", date.path("status").asText());
		assertEquals("NOT_APPLICABLE", find(report.path("fields"), "vatAmount", null).path("status").asText());
		assertEquals("ABSENT_OPTIONAL", find(report.path("fields"), "buyerTaxCode", null).path("status").asText());
		JsonNode rate = find(report.path("items"), "taxRate", 2);
		assertEquals("KCT", rate.path("rawValue").asText());
		assertEquals("NOT_APPLICABLE", rate.path("status").asText());
		assertEquals("50.000", find(report.path("items"), "unitPrice", 1).path("rawValue").asText());

		String auth = token();
		mvc.perform(get("/api/v1/documents/" + id + "/field-validation").header("Authorization", auth))
				.andExpect(status().isOk()).andExpect(jsonPath("$.documentType").value("VAT_INVOICE"))
				.andExpect(jsonPath("$.version").value(1));
		var listed = mvc.perform(get("/api/v1/documents/" + id + "/extracted-fields").header("Authorization", auth))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertFalse(listed.contains(FieldValidationReport.FIELD), "the report is not an editable extracted field");
	}

	@Test void reprocessingReplacesTheReportInsteadOfAddingRows() throws Exception {
		Long id = newDocument().getId();
		persist(id, KCT_INVOICE);
		persist(id, KCT_INVOICE.replace("Công ty Sách A", "Công ty Sách A2"));
		assertEquals("Công ty Sách A2", find(report(id).path("fields"), "sellerName", null).path("rawValue").asText());
	}

	@Test void accountantEditsAndDeliberatelyClearedFieldsAreNeverOverwrittenOrRestored() throws Exception {
		Long id = newDocument().getId();
		persist(id, KCT_INVOICE);
		Invoice edited = invoices.findByDocumentId(id).orElseThrow();
		edited.setSellerName("Tên đã sửa"); edited.setBuyerName(null); edited.setAiGenerated(false);
		invoices.saveAndFlush(edited); em.clear();

		persist(id, KCT_INVOICE.replace("Công ty Sách A", "Tên AI mới"));

		Invoice kept = invoices.findByDocumentId(id).orElseThrow();
		assertEquals("Tên đã sửa", kept.getSellerName());
		assertNull(kept.getBuyerName(), "a field the accountant cleared is not filled back in");
		assertFalse(kept.isAiGenerated());
		JsonNode report = report(id);
		assertFalse(report.path("applied").asBoolean());
		assertEquals("Tên AI mới", find(report.path("fields"), "sellerName", null).path("rawValue").asText(),
				"the AI reading is still shown for comparison");
	}

	static final String DISCOUNT_INVOICE = """
		{"documentType":"VAT_INVOICE","classificationConfidence":0.95,"invoice":{"invoiceNumber":"00001583","invoiceSeries":"1C25TYY",
		"invoiceDate":"2025-06-24","sellerName":"Công ty C","sellerTaxCode":"0100109106","subtotal":2182500,"vatAmount":174600,
		"totalAmount":2357100,"items":[
		{"productName":"Ván LMR 17 mm","unit":"Tấm","quantity":3000,"unitPrice":444444.44,"taxRate":0.08,"taxAmount":40000,"amount":1293333},
		{"productName":"Ván LMR 8 mm","unit":"Tấm","quantity":3000,"unitPrice":305555.56,"taxRate":0.08,"taxAmount":27500,"amount":889167}]},
		"warnings":["DOCUMENT_TYPE_REASON: hóa đơn GTGT"]}
		""";

	@Test void invoiceApiShowsRowDiscountsAndHidesThemOnceARowIsChanged() throws Exception {
		Long id = newDocument().getId();
		persist(id, DISCOUNT_INVOICE);
		Invoice stored = invoices.findByDocumentId(id).orElseThrow();
		assertEquals(0, new BigDecimal("3").compareTo(stored.getItems().getFirst().getQuantity()));
		assertNull(stored.getItems().getFirst().getTaxAmount());

		String auth = token();
		mvc.perform(get("/api/v1/documents/" + id + "/invoice").header("Authorization", auth)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].discountAmount").value(40000))
				.andExpect(jsonPath("$.items[1].discountAmount").value(27500));

		stored.getItems().get(1).setAmount(new BigDecimal("900000"));
		invoices.saveAndFlush(stored); em.clear();
		mvc.perform(get("/api/v1/documents/" + id + "/invoice").header("Authorization", auth)).andExpect(status().isOk())
				.andExpect(jsonPath("$.items[0].discountAmount").value(40000))
				.andExpect(jsonPath("$.items[1].discountAmount").value(org.hamcrest.Matchers.nullValue()));
	}

	@Test void approvedDocumentsAreNotTouched() throws Exception {
		Document document = newDocument();
		persist(document.getId(), KCT_INVOICE);
		Document approved = documents.findById(document.getId()).orElseThrow();
		approved.setReviewStatus(ReviewStatus.APPROVED); documents.saveAndFlush(approved); em.clear();
		String before = report(document.getId()).toString();

		persist(document.getId(), KCT_INVOICE.replace("Công ty Sách A", "Khác"));

		assertEquals("Công ty Sách A", invoices.findByDocumentId(document.getId()).orElseThrow().getSellerName());
		assertEquals(before, report(document.getId()).toString());
	}

	private static JsonNode find(JsonNode array, String field, Integer row) {
		for (JsonNode node : array)
			if (field.equals(node.path("field").asText()) && (row == null ? node.path("row").isNull() || node.path("row").isMissingNode()
					: node.path("row").asInt() == row)) return node;
		throw new AssertionError("no " + field + " row " + row + " in " + array);
	}
}
