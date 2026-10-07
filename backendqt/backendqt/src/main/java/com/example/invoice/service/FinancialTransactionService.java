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
import com.example.invoice.repository.ClassificationRepository;
import java.util.Optional;
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
	private final ClassificationRepository classificationRepository;
	private final UserService userService;

	/** Creates the financial consequence of an approved document once, when AI/manual data is sufficient. */
	@Transactional
	public Optional<FinancialTransaction> createFromApprovedDocument(Document document, User actor) {
		if (document == null || document.getCompany() == null || document.getTransactionAssessmentType() == null) return Optional.empty();
		String type = document.getTransactionAssessmentType();
		if (!"INCOME".equals(type) && !"EXPENSE".equals(type)) return Optional.empty();
		Invoice invoice = invoiceRepository.findByDocumentId(document.getId()).orElse(null);
		if (transactionRepository.existsByDocumentId(document.getId())
				|| (invoice != null && invoice.getId() != null && transactionRepository.existsByInvoiceId(invoice.getId()))) return Optional.empty();
		if (invoice == null || invoice.getTotalAmount() == null) return Optional.empty();

		FinancialTransaction transaction = new FinancialTransaction();
		transaction.setCompany(document.getCompany());
		transaction.setCreatedBy(actor);
		transaction.setTransactionType(type);
		transaction.setAmount(invoice.getTotalAmount());
		transaction.setTransactionDate(invoice.getInvoiceDate() == null ? LocalDate.now() : invoice.getInvoiceDate());
		transaction.setDescription("Chứng từ #" + document.getId());
		transaction.setPaymentMethod(invoice.getPaymentMethod());
		transaction.setInvoice(invoice);
		classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(document.getId())
				.map(com.example.invoice.entity.Classification::getAccountingCategory)
				.ifPresent(transaction::setAccountingCategory);
		transaction.setStatus("ACTIVE");
		return Optional.of(transactionRepository.save(transaction));
	}

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
			throw new BadRequestException("Không thể chuyển giao dịch sang công ty khác");
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
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chứng từ"));
		Invoice invoice = request.invoiceId() == null ? null : invoiceRepository.findById(request.invoiceId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn"));
		if (document != null && invoice != null) throw new BadRequestException("Chỉ được liên kết chứng từ hoặc hóa đơn, không được chọn cả hai");
		if (document != null && !document.getCompany().getId().equals(transaction.getCompany().getId())) throw new BadRequestException("Chứng từ thuộc công ty khác");
		if (invoice != null && !invoice.getDocument().getCompany().getId().equals(transaction.getCompany().getId())) throw new BadRequestException("Hóa đơn thuộc công ty khác");
		AccountingCategory category = request.categoryId() == null ? null : categoryRepository.findById(request.categoryId())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục kế toán"));
		if (category != null && category.getCompany() != null && !category.getCompany().getId().equals(transaction.getCompany().getId())) throw new BadRequestException("Danh mục thuộc công ty khác");
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
		FinancialTransaction transaction = transactionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch tài chính"));
		if (!isAdmin(user) && (user.getCompany() == null || !transaction.getCompany().getId().equals(user.getCompany().getId()))) throw new ResourceNotFoundException("Không tìm thấy giao dịch tài chính");
		return transaction;
	}

	private Long resolveCompanyId(User user, Long requestedCompanyId) {
		if (isAdmin(user) && requestedCompanyId != null) return requestedCompanyId;
		if (user.getCompany() == null) throw new BadRequestException("Người dùng hiện tại chưa được gán vào công ty");
		if (requestedCompanyId != null && !requestedCompanyId.equals(user.getCompany().getId())) throw new BadRequestException("Bạn không thể truy cập công ty khác");
		return user.getCompany().getId();
	}

	private Company loadCompany(Long companyId) {
		return companyRepository.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công ty"));
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
