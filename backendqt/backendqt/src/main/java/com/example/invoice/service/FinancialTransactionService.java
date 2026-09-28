package com.example.invoice.service;

import com.example.invoice.dto.accounting.FinancialTransactionRequest;
import com.example.invoice.dto.accounting.FinancialTransactionResponse;
import com.example.invoice.entity.AccountingCategory;
import com.example.invoice.entity.Company;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.FinancialTransaction;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.User;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.CompanyRepository;
import com.example.invoice.repository.DocumentRepository;
import com.example.invoice.repository.FinancialTransactionRepository;
import com.example.invoice.repository.InvoiceRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FinancialTransactionService {
	private final FinancialTransactionRepository transactionRepository;
	private final AccountingCategoryRepository categoryRepository;
	private final CompanyRepository companyRepository;
	private final DocumentRepository documentRepository;
	private final InvoiceRepository invoiceRepository;
	private final UserService userService;

	@Transactional(readOnly = true)
	public Page<FinancialTransactionResponse> findAll(LocalDate dateFrom, LocalDate dateTo, String transactionType,
			Long categoryId, Long documentId, Long invoiceId, Long companyId, Pageable pageable, Authentication authentication) {
		User user = userService.loadCurrent(authentication);
		Long permittedCompanyId = resolveCompanyId(user, companyId);
		Specification<FinancialTransaction> specification = (root, query, builder) -> builder.equal(root.get("company").get("id"), permittedCompanyId);
		if (dateFrom != null) specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("transactionDate"), dateFrom));
		if (dateTo != null) specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("transactionDate"), dateTo));
		if (transactionType != null && !transactionType.isBlank()) specification = specification.and((root, query, builder) -> builder.equal(root.get("transactionType"), transactionType));
		if (categoryId != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("accountingCategory").get("id"), categoryId));
		if (documentId != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("document").get("id"), documentId));
		if (invoiceId != null) specification = specification.and((root, query, builder) -> builder.equal(root.get("invoice").get("id"), invoiceId));
		return transactionRepository.findAll(specification, pageable).map(this::toResponse);
	}

	@Transactional
	public FinancialTransactionResponse create(FinancialTransactionRequest request, Authentication authentication) {
		User user = userService.loadCurrent(authentication);
		FinancialTransaction transaction = new FinancialTransaction();
		transaction.setCompany(loadCompany(resolveCompanyId(user, request.companyId())));
		transaction.setCreatedBy(user);
		apply(transaction, request);
		transaction.setStatus("ACTIVE");
		return toResponse(transactionRepository.save(transaction));
	}

	@Transactional
	public FinancialTransactionResponse update(Long id, FinancialTransactionRequest request, Authentication authentication) {
		FinancialTransaction transaction = loadForCompany(id, userService.loadCurrent(authentication));
		if (request.companyId() != null && !transaction.getCompany().getId().equals(request.companyId())) {
			throw new BadRequestException("A transaction cannot be moved to another company");
		}
		apply(transaction, request);
		return toResponse(transaction);
	}

	@Transactional
	public void delete(Long id, Authentication authentication) {
		transactionRepository.delete(loadForCompany(id, userService.loadCurrent(authentication)));
	}

	private void apply(FinancialTransaction transaction, FinancialTransactionRequest request) {
		Document document = request.documentId() == null ? null : documentRepository.findById(request.documentId())
				.orElseThrow(() -> new ResourceNotFoundException("Document not found"));
		Invoice invoice = request.invoiceId() == null ? null : invoiceRepository.findById(request.invoiceId())
				.orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
		if (document != null && invoice != null) throw new BadRequestException("Link either a document or an invoice, not both");
		if (document != null && !document.getCompany().getId().equals(transaction.getCompany().getId())) throw new BadRequestException("Document belongs to another company");
		if (invoice != null && !invoice.getDocument().getCompany().getId().equals(transaction.getCompany().getId())) throw new BadRequestException("Invoice belongs to another company");
		AccountingCategory category = request.categoryId() == null ? null : categoryRepository.findById(request.categoryId())
				.orElseThrow(() -> new ResourceNotFoundException("Accounting category not found"));
		if (category != null && category.getCompany() != null && !category.getCompany().getId().equals(transaction.getCompany().getId())) throw new BadRequestException("Category belongs to another company");
		transaction.setTransactionType(request.transactionType());
		transaction.setAmount(request.amount());
		transaction.setTransactionDate(request.transactionDate());
		transaction.setDescription(request.description());
		transaction.setAccountingCategory(category);
		transaction.setDocument(document);
		transaction.setInvoice(invoice);
		transaction.setPaymentMethod(request.paymentMethod());
	}

	private FinancialTransaction loadForCompany(Long id, User user) {
		FinancialTransaction transaction = transactionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Financial transaction not found"));
		if (!isAdmin(user) && (user.getCompany() == null || !transaction.getCompany().getId().equals(user.getCompany().getId()))) throw new ResourceNotFoundException("Financial transaction not found");
		return transaction;
	}

	private Long resolveCompanyId(User user, Long requestedCompanyId) {
		if (isAdmin(user) && requestedCompanyId != null) return requestedCompanyId;
		if (user.getCompany() == null) throw new BadRequestException("The current user is not assigned to a company");
		if (requestedCompanyId != null && !requestedCompanyId.equals(user.getCompany().getId())) throw new BadRequestException("You cannot access another company");
		return user.getCompany().getId();
	}

	private Company loadCompany(Long companyId) {
		return companyRepository.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Company not found"));
	}

	private boolean isAdmin(User user) {
		return user.getUserRoles().stream().anyMatch(assignment -> "ADMIN".equals(assignment.getRole().getCode()));
	}

	private FinancialTransactionResponse toResponse(FinancialTransaction item) {
		AccountingCategory category = item.getAccountingCategory();
		return new FinancialTransactionResponse(item.getId(), item.getTransactionType(), item.getAmount(), item.getTransactionDate(), item.getDescription(),
				category == null ? null : category.getId(), category == null ? null : category.getCategoryName(), item.getCompany().getId(),
				item.getDocument() == null ? null : item.getDocument().getId(), item.getInvoice() == null ? null : item.getInvoice().getId(),
				item.getPaymentMethod(), item.getStatus(), item.getCreatedBy() == null ? null : item.getCreatedBy().getId(), item.getCreatedAt(), item.getUpdatedAt());
	}
}
