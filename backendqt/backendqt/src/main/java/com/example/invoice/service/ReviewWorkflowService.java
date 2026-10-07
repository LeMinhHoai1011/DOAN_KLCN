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
	private final DocumentReviewRepository documentReviewRepository;
	private final DocumentService documentService;
	private final UserService userService;
	private final FinancialTransactionService financialTransactionService;

	public List<DocumentReview> findByDocumentId(Long documentId) {
		return documentReviewRepository.findByDocumentId(documentId);
	}

	@Transactional
	public DocumentReview createReview(Long documentId, ReviewType type, String note, Authentication authentication) {
		Document document = documentService.load(documentId);
		User reviewer = authentication == null ? null : userService.loadCurrent(authentication);
		DocumentReview review = new DocumentReview();
		review.setDocument(document);
		review.setReviewer(reviewer);
		review.setReviewType(type == null ? ReviewType.FULL_REVIEW : type);
		review.setReviewStatus(ReviewStatus.PENDING);
		review.setReviewNote(note);
		return documentReviewRepository.save(review);
	}

	@Transactional
	public DocumentReview completeReview(Long reviewId, ReviewStatus status, String note) {
		DocumentReview review = documentReviewRepository.findById(reviewId)
				.orElseThrow(() -> new com.example.invoice.exception.ResourceNotFoundException("Không tìm thấy lượt duyệt"));
		review.setReviewStatus(status);
		review.setReviewNote(note);
		review.setReviewedAt(LocalDateTime.now());
		return review;
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
		Document document = documentService.load(documentId); // enforces company and employee ownership
		String normalizedAction = action == null ? "" : action.trim().toUpperCase(java.util.Locale.ROOT);
		switch (normalizedAction) {
		case "SUBMIT" -> {
			requireEmployee(actor);
			require(document.getStatus() == DocumentStatus.UPLOADED && document.getReviewStatus() == ReviewStatus.PENDING,
					"Chỉ có thể gửi chứng từ đã tải lên và đang chờ duyệt");
			append(document, actor, ReviewStatus.PENDING, "SUBMIT", note);
		}
		case "START_REVIEW" -> {
			requireReviewer(actor);
			require(document.getStatus() == DocumentStatus.PROCESSED || document.getStatus() == DocumentStatus.NEED_REVIEW,
					"Chỉ có thể đưa chứng từ đã xử lý vào quy trình duyệt");
			document.setStatus(DocumentStatus.NEED_REVIEW);
			append(document, actor, ReviewStatus.PENDING, "START_REVIEW", note);
		}
		case "APPROVE" -> {
			requireReviewer(actor);
			require(document.getStatus() == DocumentStatus.PROCESSED || document.getStatus() == DocumentStatus.NEED_REVIEW,
					"Chỉ có thể phê duyệt chứng từ đã xử lý");
			document.setReviewStatus(ReviewStatus.APPROVED); document.setStatus(DocumentStatus.COMPLETED);
			append(document, actor, ReviewStatus.APPROVED, "APPROVE", note);
			financialTransactionService.createFromApprovedDocument(document, actor);
		}
		case "REJECT" -> {
			requireReviewer(actor); requireNote(note, "Vui lòng nhập lý do từ chối");
			require(document.getStatus() == DocumentStatus.PROCESSED || document.getStatus() == DocumentStatus.NEED_REVIEW,
					"Chỉ có thể từ chối chứng từ đã xử lý");
			document.setReviewStatus(ReviewStatus.REJECTED); document.setStatus(DocumentStatus.NEED_REVIEW);
			append(document, actor, ReviewStatus.REJECTED, "REJECT", note);
		}
		case "REQUEST_INFO" -> {
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
		return documentService.toResponse(document);
	}

	private void append(Document document, User actor, ReviewStatus status, String action, String note) {
		DocumentReview review = new DocumentReview();
		review.setDocument(document); review.setReviewer(actor); review.setReviewType(ReviewType.FULL_REVIEW);
		String message = action + (note == null || note.isBlank() ? "" : ": " + note.trim());
		review.setReviewStatus(status); review.setReviewNote(message.substring(0, Math.min(1000, message.length())));
		review.setReviewedAt(LocalDateTime.now()); documentReviewRepository.save(review);
	}

	private void requireEmployee(User user) { require(hasRole(user, "EMPLOYEE") || hasRole(user, "USER"), "Chỉ chủ sở hữu chứng từ mới có thể thực hiện thao tác này"); }
	private void requireReviewer(User user) { require(hasRole(user, "ACCOUNTANT") || hasRole(user, "ADMIN"), "Chỉ kế toán hoặc quản trị viên mới có thể thực hiện thao tác này"); }
	private void requireNote(String note, String message) { require(note != null && !note.trim().isBlank(), message); }
	private void require(boolean condition, String message) { if (!condition) throw new BadRequestException(message); }
	private boolean hasRole(User user, String role) { return user.getUserRoles().stream().anyMatch(item -> role.equals(item.getRole().getCode())); }
}
