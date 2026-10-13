package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.invoice.dto.reconciliation.ReconciliationResponse;
import com.example.invoice.entity.*;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {
	@Mock DocumentService docs;
	@Mock InvoiceRepository invoices;
	@Mock ReconciliationRepository reconciliations;
	@Mock ReconciliationBatchRepository batches;
	@Mock UserService users;

	ReconciliationService service() {
		lenient().when(batches.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		lenient().when(reconciliations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		return new ReconciliationService(docs, invoices, reconciliations, batches, users);
	}

	Document doc() {
		Document document = new Document();
		document.setId(1L);
		Company company = new Company();
		company.setId(1L);
		document.setCompany(company);
		return document;
	}

	Invoice invoice(BigDecimal line, BigDecimal subtotal, BigDecimal vat, BigDecimal total) {
		Invoice invoice = new Invoice();
		invoice.setInvoiceDate(java.time.LocalDate.now());
		invoice.setSubtotal(subtotal);
		invoice.setVatAmount(vat);
		invoice.setTotalAmount(total);
		InvoiceItem item = item(BigDecimal.ONE, new BigDecimal("100"), line, new BigDecimal("10"), new BigDecimal("10"));
		invoice.setItems(List.of(item));
		return invoice;
	}

	InvoiceItem item(BigDecimal quantity, BigDecimal price, BigDecimal amount, BigDecimal rate, BigDecimal vat) {
		InvoiceItem item = new InvoiceItem();
		item.setQuantity(quantity);
		item.setUnitPrice(price);
		item.setAmount(amount);
		item.setTaxRate(rate);
		item.setTaxAmount(vat);
		return item;
	}

	List<ReconciliationResponse> run(Invoice invoice) {
		Document document = doc();
		when(docs.load(1L)).thenReturn(document);
		when(invoices.findByDocumentId(1L)).thenReturn(Optional.of(invoice));
		when(users.loadCurrent(null)).thenReturn(new User());
		return service().run(1L, null);
	}

	ReconciliationResponse rule(List<ReconciliationResponse> results, String type) {
		return results.stream().filter(result -> type.equals(result.checkType())).findFirst().orElseThrow();
	}

	@Test void noItemsWithPositiveSubtotalIsInsufficientWithoutFalseNegativeDifference() {
		Invoice invoice = invoice(null, new BigDecimal("14800000"), new BigDecimal("1184000"), new BigDecimal("15984000"));
		invoice.setItems(List.of());
		ReconciliationResponse subtotal = rule(run(invoice), "SUBTOTAL");
		assertEquals("INSUFFICIENT_DATA", subtotal.status());
		assertEquals(new BigDecimal("14800000.00"), subtotal.expectedAmount());
		assertNull(subtotal.actualAmount());
		assertNull(subtotal.differenceAmount());
	}

	@Test void noItemsWithPositiveVatIsInsufficientWithoutFalseNegativeDifference() {
		Invoice invoice = invoice(null, new BigDecimal("14800000"), new BigDecimal("1184000"), new BigDecimal("15984000"));
		invoice.setItems(List.of());
		ReconciliationResponse vat = rule(run(invoice), "VAT_TOTAL");
		assertEquals("INSUFFICIENT_DATA", vat.status());
		assertEquals(new BigDecimal("1184000.00"), vat.expectedAmount());
		assertNull(vat.actualAmount());
		assertNull(vat.differenceAmount());
		assertEquals("MATCHED", rule(run(invoice), "TOTAL").status());
	}

	@Test void completeItemsAndTotalsMatch() {
		assertTrue(run(invoice(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"),
				new BigDecimal("110"))).stream().allMatch(result -> "MATCHED".equals(result.status())));
	}

	@Test void completeItemsButTotalsDiffer() {
		List<ReconciliationResponse> results = run(invoice(new BigDecimal("90"), new BigDecimal("80"),
				new BigDecimal("10"), new BigDecimal("95")));
		assertEquals("MISMATCH", rule(results, "ITEM_1_AMOUNT").status());
		assertEquals("MISMATCH", rule(results, "SUBTOTAL").status());
		assertEquals("MISMATCH", rule(results, "TOTAL").status());
	}

	@Test void missingQuantityOrUnitPriceNeverCalculatesZero() {
		Invoice invoice = invoice(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"),
				new BigDecimal("110"));
		invoice.getItems().getFirst().setQuantity(null);
		ReconciliationResponse amount = rule(run(invoice), "ITEM_1_AMOUNT");
		assertEquals("INSUFFICIENT_DATA", amount.status());
		assertNull(amount.actualAmount());
		assertNull(amount.differenceAmount());
	}

	@Test void supportsEightAndTenPercentVatLines() {
		Invoice invoice = new Invoice();
		invoice.setInvoiceDate(java.time.LocalDate.now());
		invoice.setSubtotal(new BigDecimal("200"));
		invoice.setVatAmount(new BigDecimal("18"));
		invoice.setTotalAmount(new BigDecimal("218"));
		invoice.setItems(List.of(
				item(BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("8"), new BigDecimal("8")),
				item(new BigDecimal("2"), new BigDecimal("50"), new BigDecimal("100"), new BigDecimal("0.10"), new BigDecimal("10"))));
		List<ReconciliationResponse> results = run(invoice);
		assertTrue(results.stream().allMatch(result -> "MATCHED".equals(result.status())));
		assertEquals(2, results.stream().filter(result -> result.checkType().endsWith("_VAT")).count());
	}

	@Test void decisionValidatesDocumentRelationAndStatus() {
		Reconciliation reconciliation = new Reconciliation();
		reconciliation.setId(2L);
		reconciliation.setDocument(doc());
		when(reconciliations.findById(2L)).thenReturn(Optional.of(reconciliation));
		when(docs.load(1L)).thenReturn(doc());
		when(users.loadCurrent(null)).thenReturn(new User());
		assertEquals("MATCHED", service().decide(1L, 2L, "MATCHED", "ok", null).status());
		assertThrows(IllegalArgumentException.class, () -> service().decide(1L, 2L, "BAD", null, null));
		assertThrows(ResourceNotFoundException.class, () -> service().decide(9L, 2L, "MATCHED", null, null));
	}

	@Test void rerunUpdatesExistingTypesInsteadOfCreatingDuplicates() {
		Document document = doc();
		Invoice invoice = invoice(new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("110"));
		Reconciliation old = new Reconciliation();
		old.setId(5L);
		old.setDocument(document);
		old.setComment("SUBTOTAL: old");
		ReconciliationBatch batch = new ReconciliationBatch();
		old.setReconciliationBatch(batch);
		when(docs.load(1L)).thenReturn(document);
		when(invoices.findByDocumentId(1L)).thenReturn(Optional.of(invoice));
		when(reconciliations.findByDocumentIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(old));
		service().run(1L, null);
		verify(reconciliations).save(same(old));
		verify(batches, never()).save(any());
	}
}
