package com.example.invoice.service;

import com.example.invoice.ai.InvoiceReconciliationRules;
import com.example.invoice.dto.reconciliation.ReconciliationResponse;
import com.example.invoice.entity.*;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReconciliationService {
    @org.springframework.beans.factory.annotation.Autowired private RetailInvoiceDetailsService retailDetails;
    @org.springframework.beans.factory.annotation.Autowired private DocumentAuditService documentAudit;
    private String before(Document d){return documentAudit==null?null:documentAudit.capture(d);}
    private void remember(Document d,String action,String previous){if(documentAudit!=null)documentAudit.append(d,action,previous);}

 @org.springframework.beans.factory.annotation.Autowired private PaymentReconciliationService paymentReconciliation;
 public com.example.invoice.dto.accounting.PaymentSummary runPayments(Long document,Long invoice,Long company,Authentication auth){return paymentReconciliation.run(document,invoice,company,auth);}

	private static final String MATCHED = InvoiceReconciliationRules.MATCHED;
	private static final String ACCEPTED = "ACCEPTED";
	private static final String NEED_REVIEW = "NEED_REVIEW";
	private final DocumentService documentService;
	private final InvoiceRepository invoiceRepository;
	private final ReconciliationRepository reconciliationRepository;
	private final ReconciliationBatchRepository batchRepository;
	private final UserService userService;

	@Transactional
	public List<ReconciliationResponse> run(Long documentId, Authentication authentication) {
		Document document = documentService.lockForEdit(documentId);
        String previous=before(document);
		Invoice invoice = invoiceRepository.findByDocumentId(documentId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn của chứng từ"));
		// Every explicit run appends results, preserving previous checks and manual decisions.
		ReconciliationBatch batch = batch(document, invoice, authentication);
		var response=checks(invoice).stream().map(check -> save(null, batch, document, invoice, check))
				.map(this::response).toList();
        remember(document,"RECONCILIATION",previous);return response;
	}

	@Transactional
	public ReconciliationResponse decide(Long documentId, Long id, String status, String comment,
			Authentication authentication) {
		Document document=documentService.lockForEdit(documentId);String previous=before(document);
		Reconciliation result = reconciliationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kết quả đối soát"));
		if (!result.getDocument().getId().equals(documentId))
			throw new ResourceNotFoundException("Không tìm thấy kết quả đối soát");
		documentService.load(documentId);
		// A decision never rewrites a difference as matched; ACCEPTED keeps it visible as accepted.
		if (!ACCEPTED.equals(status) && !NEED_REVIEW.equals(status))
			throw new IllegalArgumentException("Quyết định đối soát không được hỗ trợ");
		if (MATCHED.equals(result.getStatus()))
			throw new BadRequestException("Kiểm tra đã khớp, không cần xác nhận");
		if (ACCEPTED.equals(status) && (comment == null || comment.isBlank()))
			throw new BadRequestException("Vui lòng nhập lý do chấp nhận kết quả đối soát");
		var state = ReconciliationState.of(document, reconciliationRepository.findByDocumentIdOrderByCreatedAtDesc(documentId));
		if (!state.isLatest(result)) throw new BadRequestException("Chỉ xác nhận được kết quả của lần đối soát gần nhất");
		if (state.stale()) throw new BadRequestException("Hóa đơn đã thay đổi sau lần đối soát gần nhất, cần chạy lại đối soát");
		String checkType = type(result);
		result.setStatus(status);
		result.setComment(checkType + ": " + (comment == null || comment.isBlank() ? "Manual decision" : comment.trim()));
		result.setReviewedBy(userService.loadCurrent(authentication));
		result.setReviewedAt(java.time.LocalDateTime.now());
		remember(document,"RECONCILIATION_DECISION",previous);return response(result);
	}

	@Transactional(readOnly = true)
	public List<ReconciliationResponse> find(Long documentId) {
		documentService.load(documentId);
		return reconciliationRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream()
				.map(this::response).toList();
	}

	private List<Check> checks(Invoice invoice) {
        if (RetailInvoiceDetailsService.isRetail(invoice.getDocument())) {
            var view=retailDetails.read(invoice);
            var rows=invoice.getItems().stream().map(item -> new com.example.invoice.ai.RetailInvoiceRules.Row(
                    item.getQuantity(),item.getUnitPrice(),item.getAmount())).toList();
            return com.example.invoice.ai.RetailInvoiceRules.evaluate(view.details(),rows,invoice.getTotalAmount(),invoice.getVatAmount(),view.stale())
                    .stream().map(check -> new Check(check.type(),check.declared(),check.calculated(),check.missing(),check.message())).toList();
        }
		var rows = (invoice.getItems() == null ? List.<InvoiceItem>of() : invoice.getItems()).stream()
				.map(item -> new InvoiceReconciliationRules.Row(item.getQuantity(), item.getUnitPrice(), item.getTaxRate(),
						item.getTaxAmount(), item.getAmount())).toList();
		return InvoiceReconciliationRules.evaluate(rows, invoice.getSubtotal(), invoice.getVatAmount(), invoice.getTotalAmount())
				.stream().map(check -> new Check(check.type(), check.declared(), check.calculated(), check.missing(), check.message())).toList();
	}

	private Reconciliation save(Reconciliation current, ReconciliationBatch batch, Document document,
			Invoice invoice, Check check) {
		if (current != null && current.getReviewedAt() != null) return current;
		Reconciliation result = current == null ? new Reconciliation() : current;
		result.setReconciliationBatch(batch);
		result.setDocument(document);
		result.setInvoice(invoice);
		result.setExpectedAmount(roundNullable(check.declared()));
		result.setActualAmount(roundNullable(check.calculated()));
		BigDecimal difference = check.missing() || check.declared() == null || check.calculated() == null
				? null : InvoiceReconciliationRules.difference(check.declared(), check.calculated());
		result.setDifferenceAmount(difference);
		result.setStatus(InvoiceReconciliationRules.status(check.declared(), check.calculated(), check.missing()));
		result.setComment(check.type() + ": " + (check.missing() ? "INSUFFICIENT_DATA; " : "")
				+ check.message() + "; rounding=HALF_UP scale=2");
		return reconciliationRepository.save(result);
	}

	private ReconciliationBatch batch(Document document, Invoice invoice, Authentication authentication) {
		User user = userService.loadCurrent(authentication);
		ReconciliationBatch batch = new ReconciliationBatch();
		batch.setCompany(document.getCompany());
		batch.setBatchCode("DOC-" + document.getId() + "-" + System.currentTimeMillis());
		batch.setPeriodFrom(invoice.getInvoiceDate() == null ? LocalDate.now() : invoice.getInvoiceDate());
		batch.setPeriodTo(batch.getPeriodFrom());
		batch.setStatus("COMPLETED");
		batch.setCreatedBy(user);
		return batchRepository.save(batch);
	}

	private String type(Reconciliation result) {
		return result.getComment() == null ? "UNKNOWN_" + result.getId() : result.getComment().split(":", 2)[0];
	}

	private ReconciliationResponse response(Reconciliation result) {
		return new ReconciliationResponse(result.getId(), type(result), result.getExpectedAmount(),
				result.getActualAmount(), result.getDifferenceAmount(), result.getStatus(), result.getComment(),
				result.getReconciliationBatch() == null ? null : result.getReconciliationBatch().getId(), result.getReviewedAt());
	}

	private BigDecimal roundNullable(BigDecimal value) {
		return value == null ? null : round(value);
	}

	private BigDecimal round(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private record Check(String type, BigDecimal declared, BigDecimal calculated, boolean missing, String message) {}
}
