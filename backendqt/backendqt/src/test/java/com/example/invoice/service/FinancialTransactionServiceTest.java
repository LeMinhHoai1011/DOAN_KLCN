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
	void approvalNeverCreatesCashOrAssumesInvoiceTotalWasPaid() {
		Document document=new Document();document.setId(8L);document.setCompany(new Company());document.setTransactionAssessmentType("EXPENSE");
		assertEquals(Optional.empty(),service().createFromApprovedDocument(document,new User()));
		assertEquals(Optional.empty(),service().createFromApprovedDocument(document,new User()));
		verify(transactions,never()).save(any());
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
