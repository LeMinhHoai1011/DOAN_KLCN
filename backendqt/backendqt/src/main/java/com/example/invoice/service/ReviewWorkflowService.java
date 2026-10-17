package com.example.invoice.service;

import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentReview;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.entity.ReviewType;
import com.example.invoice.entity.User;
import com.example.invoice.repository.DocumentReviewRepository;
import com.example.invoice.exception.BadRequestException;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewWorkflowService {
    @org.springframework.beans.factory.annotation.Autowired private DocumentAuditService documentAudit;
    private String before(Document d){return documentAudit==null?null:documentAudit.capture(d);}
    private void remember(Document d,String action,String previous){if(documentAudit!=null)documentAudit.append(d,action,previous);}

	private final DocumentReviewRepository documentReviewRepository;
	private final DocumentService documentService;
	private final UserService userService;
	private final FinancialTransactionService financialTransactionService;

	@Transactional(readOnly = true)
	public com.example.invoice.dto.document.DocumentResponse currentResponse(Long id) { return documentService.toResponse(documentService.load(id)); }

	public List<DocumentReview> findByDocumentId(Long documentId) {
		documentService.load(documentId);
		return documentReviewRepository.findByDocumentId(documentId);
	}

	@Transactional
	public DocumentReview createReview(Long documentId, ReviewType type, String note, Authentication authentication) {
		throw new BadRequestException("Tạo lượt kiểm duyệt phải thông qua workflow chứng từ");
	}

	@Transactional
	public DocumentReview completeReview(Long reviewId, ReviewStatus status, String note) {
		throw new BadRequestException("Hoàn tất kiểm duyệt phải thông qua workflow chứng từ");
	}

	/**
	 * State vocabulary deliberately reuses the persisted enums:
	 * UPLOADED/PENDING -> submit; NEED_REVIEW/PENDING -> review; APPROVED/COMPLETED,
	 * REJECTED/NEED_REVIEW, CORRECTED/NEED_REVIEW (request information); a corrected
	 * or rejected document can be resubmitted to UPLOADED/PENDING.
	 */
	@Transactional
	public com.example.invoice.dto.document.DocumentResponse execute(Long documentId, String action, String note,
			Authentication authentication) {
		User actor = userService.loadCurrent(authentication);
		Document document = documentService.lockForWorkflow(documentId); // scope, lock and refreshed state
		require(document.getReviewStatus() != ReviewStatus.APPROVED && document.getStatus() != DocumentStatus.COMPLETED, "Chứng từ đã duyệt hoặc đã khóa");
		String previous=before(document);
        String normalizedAction = action == null ? "" : action.trim().toUpperCase(java.util.Locale.ROOT);
		switch (normalizedAction) {
		case "SUBMIT" -> {
			require(!latestActionIs(documentId, "SUBMIT"), "Chứng từ đã được gửi xử lý");
			requireEmployee(actor);
			require(document.getStatus() == DocumentStatus.UPLOADED && document.getReviewStatus() == ReviewStatus.PENDING,
					"Chỉ có thể gửi chứng từ đã tải lên và đang chờ duyệt");
			append(document, actor, ReviewStatus.PENDING, "SUBMIT", note);
		}
		case "START_REVIEW" -> {
			require(document.getReviewStatus() == ReviewStatus.PENDING, "Chứng từ đã trả lại cần được gửi lại trước khi duyệt");
			require(!latestActionIs(documentId, "START_REVIEW"), "Chứng từ đã bắt đầu duyệt");
			requireReviewer(actor);
			require(document.getStatus() == DocumentStatus.PROCESSED || document.getStatus() == DocumentStatus.NEED_REVIEW,
					"Chỉ có thể đưa chứng từ đã xử lý vào quy trình duyệt");
			document.setStatus(DocumentStatus.NEED_REVIEW);
			append(document, actor, ReviewStatus.PENDING, "START_REVIEW", note);
		}
		case "APPROVE" -> {
			require(document.getReviewStatus() == ReviewStatus.PENDING, "Chứng từ bị trả lại phải được gửi lại trước khi phê duyệt");
			requireReviewer(actor);
			require(document.getStatus() == DocumentStatus.PROCESSED || document.getStatus() == DocumentStatus.NEED_REVIEW,
					"Chỉ có thể phê duyệt chứng từ đã xử lý");
			document.setReviewStatus(ReviewStatus.APPROVED); document.setStatus(DocumentStatus.COMPLETED);
			append(document, actor, ReviewStatus.APPROVED, "APPROVE", note);
			// Approval is a review decision only; payment requires explicit cash confirmation.
		}
		case "REJECT" -> {
			require(document.getReviewStatus() == ReviewStatus.PENDING, "Chứng từ đã trả lại cần được gửi lại trước khi xử lý tiếp");
			requireReviewer(actor); requireNote(note, "Vui lòng nhập lý do từ chối");
			require(document.getStatus() == DocumentStatus.PROCESSED || document.getStatus() == DocumentStatus.NEED_REVIEW,
					"Chỉ có thể từ chối chứng từ đã xử lý");
			document.setReviewStatus(ReviewStatus.REJECTED); document.setStatus(DocumentStatus.NEED_REVIEW);
			append(document, actor, ReviewStatus.REJECTED, "REJECT", note);
		}
		case "REQUEST_INFO" -> {
			require(document.getReviewStatus() == ReviewStatus.PENDING, "Chứng từ đã trả lại cần được gửi lại trước khi xử lý tiếp");
			requireReviewer(actor); requireNote(note, "Vui lòng nhập lý do yêu cầu bổ sung thông tin");
			require(document.getStatus() == DocumentStatus.PROCESSED || document.getStatus() == DocumentStatus.NEED_REVIEW,
					"Chỉ có thể yêu cầu bổ sung thông tin cho chứng từ đã xử lý");
			document.setReviewStatus(ReviewStatus.CORRECTED); document.setStatus(DocumentStatus.NEED_REVIEW);
			append(document, actor, ReviewStatus.CORRECTED, "REQUEST_INFO", note);
		}
		case "RESUBMIT" -> {
			requireEmployee(actor);
			require(document.getStatus() == DocumentStatus.NEED_REVIEW && (document.getReviewStatus() == ReviewStatus.REJECTED || document.getReviewStatus() == ReviewStatus.CORRECTED),
					"Chỉ có thể gửi lại chứng từ đã bị từ chối hoặc được yêu cầu bổ sung thông tin");
			document.setReviewStatus(ReviewStatus.PENDING); document.setStatus(DocumentStatus.UPLOADED);
			append(document, actor, ReviewStatus.PENDING, "RESUBMIT", note);
		}
		default -> throw new BadRequestException("Thao tác quy trình không được hỗ trợ");
		}
		remember(document,"WORKFLOW_"+normalizedAction,previous);
        return documentService.toResponse(document);
	}

	private boolean latestActionIs(Long documentId, String action) {
		DocumentReview latest = documentReviewRepository.findByDocumentId(documentId).stream()
				.max(java.util.Comparator.comparing(DocumentReview::getId, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))).orElse(null);
		return latest != null && latest.getReviewNote() != null
				&& (latest.getReviewNote().equals(action) || latest.getReviewNote().startsWith(action + ":"));
	}

	private void append(Document document, User actor, ReviewStatus status, String action, String note) {
		DocumentReview review = new DocumentReview();
		review.setDocument(document); review.setReviewer(actor); review.setReviewType(ReviewType.FULL_REVIEW);
		String message = action + (note == null || note.isBlank() ? "" : ": " + note.trim());
		review.setReviewStatus(status); review.setReviewNote(message.substring(0, Math.min(1000, message.length())));
		review.setReviewedAt(LocalDateTime.now()); documentReviewRepository.save(review);
	}

	private void requireEmployee(User user) { if (!hasRole(user, "EMPLOYEE") && !hasRole(user, "USER")) throw new org.springframework.security.access.AccessDeniedException("Chỉ chủ sở hữu chứng từ mới có thể thực hiện thao tác này"); }
	private void requireReviewer(User user) { if (!hasRole(user, "ACCOUNTANT") && !hasRole(user, "ADMIN")) throw new org.springframework.security.access.AccessDeniedException("Chỉ kế toán hoặc quản trị viên mới có thể thực hiện thao tác này"); }
	private void requireNote(String note, String message) { require(note != null && !note.trim().isBlank(), message); }
	private void require(boolean condition, String message) { if (!condition) throw new BadRequestException(message); }
	private boolean hasRole(User user, String role) { return user.getUserRoles().stream().anyMatch(item -> item.getRole().isActive() && role.equals(item.getRole().getCode())); }
}
