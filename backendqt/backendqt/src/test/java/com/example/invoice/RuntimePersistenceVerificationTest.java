package com.example.invoice;

import static org.junit.jupiter.api.Assertions.*;

import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.invoice.InvoiceRequest;
import com.example.invoice.entity.*;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.*;
import com.example.invoice.service.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"app.demo-admin.enabled=true", "app.demo-users.enabled=true"})
@EnabledIfEnvironmentVariable(named = "RUNTIME_DB_TEST", matches = "true")
@Transactional
class RuntimePersistenceVerificationTest {
	@Autowired CompanyRepository companies;
	@Autowired UserRepository users;
	@Autowired DocumentRepository documents;
	@Autowired DocumentTypeRepository documentTypes;
	@Autowired InvoiceRepository invoices;
	@Autowired OCRResultRepository ocrResults;
	@Autowired ClassificationRepository classifications;
	@Autowired ExtractedFieldRepository extractedFields;
	@Autowired DocumentService documentService;
	@Autowired InvoiceService invoiceService;
	@Autowired OCRResultService ocrResultService;
	@Autowired DocumentAiResultPersistenceService persistence;

	@AfterEach void clear() { SecurityContextHolder.clearContext(); }

	@Test
	void actualPostgresPersistsOcrAiStructuresAndHandlesReprocessSafely() {
		as("admin");
		Company company = company("RUNTIME-PERSIST");
		Document document = document(company, "runtime-persistence.png");
		ocrResultService.persistExtraction(document.getId(), new DocumentTextExtractionResult(
				"[PAGE 1]\nINVOICE INV-RUNTIME-1 TOTAL 1100000", List.of(
				new DocumentTextExtractionResult.PageText(1, "INVOICE INV-RUNTIME-1 TOTAL 1100000", new BigDecimal("0.94"))),
				"tesseract", "5.5.0", "eng", "IMAGE_OCR", new BigDecimal("0.94"), 25, false, List.of()));
		persist(document.getId(), invoiceResult("VAT_INVOICE"));
		OCRResult ocr = ocrResults.findFirstByDocumentIdOrderByProcessedAtDesc(document.getId()).orElseThrow();
		Invoice invoice = invoices.findByDocumentId(document.getId()).orElseThrow();
		assertEquals("tesseract", ocr.getOcrEngine()); assertEquals(new BigDecimal("0.94"), ocr.getConfidence());
		assertEquals("IMAGE_OCR", ocr.getSourceType()); assertEquals(2, invoice.getItems().size());
		assertTrue(invoice.isAiGenerated());
		assertTrue(extractedFields.findByDocumentId(document.getId()).stream().noneMatch(f -> "invoiceNumber".equals(f.getFieldName())));
		assertTrue(classifications.findFirstByDocumentIdOrderByCreatedAtDesc(document.getId()).isPresent());

		persist(document.getId(), invoiceResult("VAT_INVOICE"));
		assertEquals(invoice.getId(), invoices.findByDocumentId(document.getId()).orElseThrow().getId());
		assertEquals(2, invoices.findByDocumentId(document.getId()).orElseThrow().getItems().size());
		persist(document.getId(), invoiceResult("VAT_INVOICE"));
		assertEquals(0, extractedFields.findByDocumentId(document.getId()).stream().filter(f -> "invoiceNumber".equals(f.getFieldName())).count());

		persist(document.getId(), nonInvoiceResult());
		assertTrue(invoices.findByDocumentId(document.getId()).isEmpty());

		persist(document.getId(), invoiceResult("VAT_INVOICE"));
		Invoice manual = invoices.findByDocumentId(document.getId()).orElseThrow();
		invoiceService.update(manual.getId(), new InvoiceRequest(document.getId(), "MANUAL-1", null, null, null, null,
				null, null, null, null, null, new BigDecimal("1200000"), List.of()));
		persist(document.getId(), nonInvoiceResult());
		assertEquals("MANUAL-1", invoices.findByDocumentId(document.getId()).orElseThrow().getInvoiceNumber());
		assertEquals(DocumentStatus.PROCESSED, documents.findById(document.getId()).orElseThrow().getStatus());
	}

	@Test
	void actualPostgresAllowsDocumentFieldWithoutInvoiceAndKeepsForeignKeysValid() {
		Company company = company("RUNTIME-NULL-INVOICE");
		Document document = document(company, "document-field.png");
		ExtractedField field = new ExtractedField();
		field.setDocument(document);
		field.setInvoice(null);
		field.setFieldName("receiptNumber");
		field.setFieldValue("R-NULL-INVOICE");
		field.setSource("AI");
		ExtractedField saved = extractedFields.saveAndFlush(field);
		assertNotNull(saved.getId());
		assertNull(extractedFields.findById(saved.getId()).orElseThrow().getInvoice());
	}

	@Test
	void actualPostgresPreservesLongInvoiceFreeTextAndReprocessesWithoutDuplicates() {
		as("admin");
		Company company = company("RUNTIME-LONG-TEXT");
		Document document = document(company, "long-address.png");
		String sellerName = "Seller ".repeat(50);
		String buyerName = "Buyer ".repeat(55);
		String sellerAddress = "123 Long Seller Address, Ward 1, District 1; ".repeat(12);
		String buyerAddress = "456 Long Buyer Address, Ward 2, District 2; ".repeat(12);
		String productName = "Detailed product description ".repeat(15);
		AiDocumentResult result = longTextInvoice(sellerName, buyerName, sellerAddress, buyerAddress, productName,
				"INV-LONG-1", "0312345678");

		persist(document.getId(), result);
		Invoice first = invoices.findByDocumentId(document.getId()).orElseThrow();
		invoices.flush();
		assertEquals(sellerName, first.getSellerName());
		assertEquals(buyerName, first.getBuyerName());
		assertEquals(sellerAddress, first.getSellerAddress());
		assertEquals(buyerAddress, first.getBuyerAddress());
		assertEquals(productName, first.getItems().getFirst().getProductName());

		persist(document.getId(), result);
		Invoice reprocessed = invoices.findByDocumentId(document.getId()).orElseThrow();
		assertEquals(first.getId(), reprocessed.getId());
		assertEquals(1, reprocessed.getItems().size());
		assertEquals(sellerAddress, reprocessed.getSellerAddress());
	}

	@Test
	void actualPostgresKeepsAbnormalIdentifiersForReviewWithoutSqlFailureOrTruncation() {
		as("admin");
		Company company = company("RUNTIME-BAD-ID");
		Document document = document(company, "bad-identifier.png");
		String invoiceNumber = "I".repeat(400);
		String taxCode = "T".repeat(500);
		AiDocumentResult result = longTextInvoice("Seller", "Buyer", "Address", "Address", "Item",
				invoiceNumber, taxCode);

		persist(document.getId(), result);
		Invoice invoice = invoices.findByDocumentId(document.getId()).orElseThrow();
		assertNull(invoice.getInvoiceNumber());
		assertNull(invoice.getSellerTaxCode());
		assertEquals(DocumentStatus.NEED_REVIEW, documents.findById(document.getId()).orElseThrow().getStatus());
		assertEquals(invoiceNumber, extractedFields.findByDocumentId(document.getId()).stream()
				.filter(f -> "invoiceNumber".equals(f.getFieldName())).findFirst().orElseThrow().getFieldValue());
		assertEquals(taxCode, extractedFields.findByDocumentId(document.getId()).stream()
				.filter(f -> "sellerTaxCode".equals(f.getFieldName())).findFirst().orElseThrow().getFieldValue());
	}

	@Test
	void actualPostgresEnforcesTwoCompanyDocumentAndInvoiceScope() {
		Company a = company("RUNTIME-A"), b = company("RUNTIME-B");
		User accountant = users.findByUsername("accountant").orElseThrow(); accountant.setCompany(a); users.saveAndFlush(accountant);
		Document docA = document(a, "a.png"), docB = document(b, "b.png");
		as("admin");
		Invoice invoiceA = invoice(docA), invoiceB = invoice(docB);
		as("accountant");
		assertEquals(docA.getId(), documentService.findById(docA.getId()).id());
		assertThrows(ResourceNotFoundException.class, () -> documentService.findById(docB.getId()));
		assertEquals(invoiceA.getId(), invoiceService.findById(invoiceA.getId()).id());
		assertThrows(ResourceNotFoundException.class, () -> invoiceService.findById(invoiceB.getId()));
		assertThrows(ResourceNotFoundException.class, () -> invoiceService.update(invoiceB.getId(), new InvoiceRequest(
				docB.getId(), "DENIED", null, null, null, null, null, null, null, null, null, null, List.of())));
		assertThrows(ResourceNotFoundException.class, () -> invoiceService.delete(invoiceB.getId()));
		as("admin");
		assertEquals(docB.getId(), documentService.findById(docB.getId()).id());
		assertEquals(invoiceB.getId(), invoiceService.findById(invoiceB.getId()).id());
	}

	private Company company(String prefix) { Company c = new Company(); c.setCompanyName(prefix + System.nanoTime()); c.setTaxCode(prefix + System.nanoTime()); return companies.saveAndFlush(c); }
	private Document document(Company c, String name) { Document d = new Document(); d.setCompany(c); d.setOriginalFileName(name); d.setFileName(name); d.setFileType("image/png"); d.setFileSize(1L); d.setFilePath("runtime/" + name); return documents.saveAndFlush(d); }
	private Invoice invoice(Document d) { Invoice i = new Invoice(); i.setDocument(d); i.setInvoiceNumber("INV-" + d.getId()); return invoices.saveAndFlush(i); }
	private void as(String username) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "runtime")); }
	private void persist(Long id, AiDocumentResult result) { persistence.persist(id, new AiDocumentResultValidator().validate(result, Set.of("VAT_INVOICE", "OTHER"))); }
	private AiDocumentResult invoiceResult(String type) { return new AiDocumentResult("ollama", "qwen3.5:9b", type, new BigDecimal("0.95"), null,
			new AiDocumentResult.AiInvoiceExtraction("INV-RUNTIME-1", null, "2026-10-02", "Seller", "0312345678", null,
					"Buyer", "0311111111", null, new BigDecimal("1000000"), new BigDecimal("100000"), new BigDecimal("1100000"), List.of(
					new AiDocumentResult.AiInvoiceItemExtraction("Item A", BigDecimal.ONE, "pcs", new BigDecimal("500000"), null, null, new BigDecimal("500000")),
					new AiDocumentResult.AiInvoiceItemExtraction("Item B", BigDecimal.ONE, "pcs", new BigDecimal("500000"), null, null, new BigDecimal("500000")))),
			List.of(new AiDocumentResult.AiExtractedField("invoiceNumber", "INV-RUNTIME-1", new BigDecimal("0.95"))), List.of(), "{}", 20); }
	private AiDocumentResult nonInvoiceResult() { return new AiDocumentResult("ollama", "qwen3.5:9b", "OTHER", new BigDecimal("0.95"),
			"receipt", null, List.of(new AiDocumentResult.AiExtractedField("receiptNumber", "R-1", new BigDecimal("0.9"))), List.of(), "{}", 20); }
	private AiDocumentResult longTextInvoice(String sellerName, String buyerName, String sellerAddress, String buyerAddress,
			String productName, String invoiceNumber, String sellerTaxCode) {
		return new AiDocumentResult("ollama", "qwen3.5:9b", "VAT_INVOICE", new BigDecimal("0.95"), null,
				new AiDocumentResult.AiInvoiceExtraction(invoiceNumber, "SERIES", "2026-10-03", sellerName, sellerTaxCode,
						sellerAddress, buyerName, "0311111111", buyerAddress, BigDecimal.TEN, BigDecimal.ONE,
						new BigDecimal("11"), List.of(new AiDocumentResult.AiInvoiceItemExtraction(productName,
								BigDecimal.ONE, "unit", BigDecimal.TEN, null, BigDecimal.ONE, new BigDecimal("11")))),
				List.of(), List.of(), "{}", 20);
	}
}
