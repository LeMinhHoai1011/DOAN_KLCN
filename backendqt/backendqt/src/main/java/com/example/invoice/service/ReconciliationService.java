package com.example.invoice.service;

import com.example.invoice.dto.reconciliation.ReconciliationResponse;
import com.example.invoice.entity.*;
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
	private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
	private static final String MATCHED = "MATCHED";
	private static final String MISMATCH = "MISMATCH";
	private static final String INSUFFICIENT_DATA = "INSUFFICIENT_DATA";
	private final DocumentService documentService;
	private final InvoiceRepository invoiceRepository;
	private final ReconciliationRepository reconciliationRepository;
	private final ReconciliationBatchRepository batchRepository;
	private final UserService userService;

	@Transactional
	public List<ReconciliationResponse> run(Long documentId, Authentication authentication) {
		Document document = documentService.load(documentId);
		Invoice invoice = invoiceRepository.findByDocumentId(documentId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn của chứng từ"));
		List<Reconciliation> existing = reconciliationRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
		Map<String, Reconciliation> byType = new LinkedHashMap<>();
		existing.forEach(result -> byType.putIfAbsent(type(result), result));
		ReconciliationBatch batch = existing.isEmpty()
				? batch(document, invoice, authentication) : existing.getFirst().getReconciliationBatch();
		return checks(invoice).stream().map(check -> save(byType.get(check.type()), batch, document, invoice, check))
				.map(this::response).toList();
	}

	@Transactional
	public ReconciliationResponse decide(Long documentId, Long id, String status, String comment,
			Authentication authentication) {
		Reconciliation result = reconciliationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kết quả đối soát"));
		if (!result.getDocument().getId().equals(documentId))
			throw new ResourceNotFoundException("Không tìm thấy kết quả đối soát");
		documentService.load(documentId);
		if (!MATCHED.equals(status) && !"NEED_REVIEW".equals(status))
			throw new IllegalArgumentException("Quyết định đối soát không được hỗ trợ");
		String checkType = type(result);
		result.setStatus(status);
		result.setComment(checkType + ": " + (comment == null ? "Manual decision" : comment));
		result.setReviewedBy(userService.loadCurrent(authentication));
		result.setReviewedAt(java.time.LocalDateTime.now());
		return response(result);
	}

	public List<ReconciliationResponse> find(Long documentId) {
		documentService.load(documentId);
		return reconciliationRepository.findByDocumentIdOrderByCreatedAtDesc(documentId).stream()
				.map(this::response).toList();
	}

	private List<Check> checks(Invoice invoice) {
		List<Check> result = new ArrayList<>();
		List<InvoiceItem> items = invoice.getItems() == null ? List.of() : invoice.getItems();
		if (items.isEmpty())
			result.add(insufficient("ITEMS_PRESENT", null, null,
					"Chưa đủ dữ liệu hàng hóa để đối soát; cần bổ sung ít nhất một dòng hàng hóa"));
		for (int index = 0; index < items.size(); index++) {
			InvoiceItem item = items.get(index);
			BigDecimal amount = multiply(item.getQuantity(), item.getUnitPrice());
			result.add(check("ITEM_" + (index + 1) + "_AMOUNT", item.getAmount(), amount,
					item.getQuantity() == null || item.getUnitPrice() == null || item.getAmount() == null,
					"Thành tiền dòng = số lượng × đơn giá; cần quantity, unitPrice và amount"));
			BigDecimal rate = rate(item.getTaxRate());
			BigDecimal vat = item.getAmount() == null || rate == null ? null : round(item.getAmount().multiply(rate));
			result.add(check("ITEM_" + (index + 1) + "_VAT", item.getTaxAmount(), vat,
					item.getAmount() == null || item.getTaxRate() == null || item.getTaxAmount() == null,
					"VAT dòng theo thuế suất; cần amount, taxRate và taxAmount"));
		}
		BigDecimal itemSum = completeSum(items.stream().map(InvoiceItem::getAmount).toList());
		BigDecimal vatSum = completeSum(items.stream().map(InvoiceItem::getTaxAmount).toList());
		result.add(check("SUBTOTAL", invoice.getSubtotal(), itemSum,
				invoice.getSubtotal() == null || items.isEmpty() || itemSum == null,
				"Tổng dòng so với tổng trước thuế; cần items[].amount và subtotal"));
		result.add(check("VAT_TOTAL", invoice.getVatAmount(), vatSum,
				invoice.getVatAmount() == null || items.isEmpty() || vatSum == null,
				"Tổng VAT dòng, hỗ trợ nhiều thuế suất; cần items[].taxAmount và vatAmount"));
		BigDecimal total = invoice.getSubtotal() == null || invoice.getVatAmount() == null
				? null : round(invoice.getSubtotal().add(invoice.getVatAmount()));
		result.add(check("TOTAL", invoice.getTotalAmount(), total,
				invoice.getSubtotal() == null || invoice.getVatAmount() == null || invoice.getTotalAmount() == null,
				"Tổng trước thuế + VAT so với thanh toán; cần subtotal, vatAmount và totalAmount"));
		return result;
	}

	private Check check(String type, BigDecimal declared, BigDecimal calculated, boolean missing, String message) {
		return missing ? insufficient(type, declared, calculated, message)
				: new Check(type, declared, calculated, false, message);
	}

	private Check insufficient(String type, BigDecimal declared, BigDecimal calculated, String message) {
		return new Check(type, declared, calculated, true, message);
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
				? null : round(check.calculated().subtract(check.declared()));
		result.setDifferenceAmount(difference);
		result.setStatus(check.missing() ? INSUFFICIENT_DATA
				: difference.abs().compareTo(TOLERANCE) <= 0 ? MATCHED : MISMATCH);
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
				result.getActualAmount(), result.getDifferenceAmount(), result.getStatus(), result.getComment());
	}

	private BigDecimal rate(BigDecimal value) {
		return value == null ? null : value.abs().compareTo(BigDecimal.ONE) <= 0 ? value : value.movePointLeft(2);
	}

	private BigDecimal multiply(BigDecimal left, BigDecimal right) {
		return left == null || right == null ? null : round(left.multiply(right));
	}

	private BigDecimal completeSum(List<BigDecimal> values) {
		if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) return null;
		return round(values.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
	}

	private BigDecimal roundNullable(BigDecimal value) {
		return value == null ? null : round(value);
	}

	private BigDecimal round(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

	private record Check(String type, BigDecimal declared, BigDecimal calculated, boolean missing, String message) {}
}
