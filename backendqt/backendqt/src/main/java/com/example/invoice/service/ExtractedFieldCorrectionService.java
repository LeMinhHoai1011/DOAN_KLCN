package com.example.invoice.service;

import com.example.invoice.dto.invoice.ExtractedFieldCorrectionRequest;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.FieldCorrection;
import com.example.invoice.entity.User;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.FieldCorrectionRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExtractedFieldCorrectionService {
	private static final Set<String> INVOICE_CORE_FIELDS = Set.of(
			"invoiceNumber", "invoiceSeries", "invoiceDate", "sellerName", "sellerTaxCode", "sellerAddress",
			"sellerPhone", "buyerName", "buyerTaxCode", "buyerAddress", "subtotal", "vatAmount", "taxAmount",
			"totalAmount", "paymentMethod", "amountInWords", "taxAuthorityCode", "signDate", "items");

	private final DocumentService documentService;
	private final UserService userService;
	private final ExtractedFieldRepository extractedFieldRepository;
	private final FieldCorrectionRepository fieldCorrectionRepository;

	@Transactional
	public void correct(Long documentId, Long fieldId, ExtractedFieldCorrectionRequest request,
			Authentication authentication) {
		User actor = userService.loadCurrent(authentication);
		if (!hasRole(actor, "ADMIN") && !hasRole(actor, "ACCOUNTANT")) {
			throw new AccessDeniedException("Chỉ kế toán hoặc quản trị viên mới có thể chỉnh sửa trường trích xuất");
		}

		Document document = documentService.load(documentId);
		if (document.getStatus() != DocumentStatus.PROCESSED
				&& document.getStatus() != DocumentStatus.NEED_REVIEW) {
			throw new BadRequestException("Chỉ có thể chỉnh sửa trường khi chứng từ đang xử lý hoặc cần duyệt");
		}
		if (document.getReviewStatus() == com.example.invoice.entity.ReviewStatus.APPROVED) {
			throw new BadRequestException("Chứng từ đã được duyệt và chỉ đọc");
		}

		ExtractedField field = extractedFieldRepository.findById(fieldId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy trường trích xuất"));
		if (!documentId.equals(field.getDocument().getId())) {
			throw new ResourceNotFoundException("Không tìm thấy trường trích xuất");
		}
		if (!"AI".equalsIgnoreCase(field.getSource())
				|| INVOICE_CORE_FIELDS.contains(field.getFieldName())) {
			throw new BadRequestException("Trường này không thuộc dữ liệu trích xuất có thể chỉnh sửa");
		}

		FieldCorrection previous = fieldCorrectionRepository
				.findFirstByFieldIdOrderByCreatedAtDescIdDesc(fieldId).orElse(null);
		FieldCorrection correction = new FieldCorrection();
		correction.setField(field);
		correction.setCorrectedBy(actor);
		correction.setOldValue(previous == null ? field.getFieldValue() : previous.getNewValue());
		correction.setNewValue(request.fieldValue().trim());
		correction.setReason("Manual correction");
		fieldCorrectionRepository.save(correction);
	}

	private boolean hasRole(User user, String roleCode) {
		return user.getUserRoles().stream()
				.anyMatch(assignment -> roleCode.equals(assignment.getRole().getCode()));
	}
}
