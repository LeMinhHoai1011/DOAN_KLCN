package com.example.invoice.service;

import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentReview;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.entity.ReviewType;
import com.example.invoice.entity.User;
import com.example.invoice.repository.DocumentReviewRepository;
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
				.orElseThrow(() -> new com.example.invoice.exception.ResourceNotFoundException("Review not found"));
		review.setReviewStatus(status);
		review.setReviewNote(note);
		review.setReviewedAt(LocalDateTime.now());
		return review;
	}
}
