package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.invoice.entity.Company;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.FinancialTransaction;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.User;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.CompanyRepository;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.FinancialTransactionRepository;
import com.example.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialTransactionServiceTest {
	@Mock FinancialTransactionRepository transactions;
	@Mock AccountingCategoryRepository categories;
	@Mock CompanyRepository companies;
	@Mock DocumentRepository documents;
	@Mock InvoiceRepository invoices;
	@Mock ClassificationRepository classifications;
	@Mock UserService users;

	@Test
	void approvedExpenseCreatesOneInvoiceLinkedTransactionAndRetryIsIdempotent() {
		Company company = new Company(); company.setId(3L);
		Document document = new Document(); document.setId(8L); document.setCompany(company);
		document.setTransactionAssessmentType("EXPENSE");
		Invoice invoice = new Invoice(); invoice.setId(12L); invoice.setDocument(document);
		invoice.setTotalAmount(new BigDecimal("108000")); invoice.setInvoiceDate(LocalDate.of(2026, 10, 6));
		User actor = new User(); actor.setId(4L);
		when(invoices.findByDocumentId(8L)).thenReturn(Optional.of(invoice));
		when(transactions.existsByDocumentId(8L)).thenReturn(false, true);
		when(transactions.existsByInvoiceId(12L)).thenReturn(false);
		when(classifications.findFirstByDocumentIdOrderByCreatedAtDesc(8L)).thenReturn(Optional.empty());
		when(transactions.save(any())).thenAnswer(call -> call.getArgument(0));
		FinancialTransactionService service = service();

		service.createFromApprovedDocument(document, actor);
		service.createFromApprovedDocument(document, actor);

		ArgumentCaptor<FinancialTransaction> saved = ArgumentCaptor.forClass(FinancialTransaction.class);
		verify(transactions).save(saved.capture());
		assertEquals("EXPENSE", saved.getValue().getTransactionType());
		assertEquals(invoice, saved.getValue().getInvoice());
		assertEquals(new BigDecimal("108000"), saved.getValue().getAmount());
	}

	@Test
	void unknownAssessmentDoesNotCreateTransaction() {
		Document document = new Document(); document.setId(9L); document.setCompany(new Company());
		document.setTransactionAssessmentType("UNKNOWN");
		service().createFromApprovedDocument(document, new User());
		verify(transactions, never()).save(any());
	}

	private FinancialTransactionService service() {
		return new FinancialTransactionService(transactions, categories, companies, documents, invoices,
				classifications, users);
	}
}
