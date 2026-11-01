package com.example.invoice.repository;

import com.example.invoice.entity.DocumentReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentReviewRepository extends JpaRepository<DocumentReview, Long> {
	List<DocumentReview> findByDocumentId(Long documentId);

	@org.springframework.data.jpa.repository.Query("""
			select r from DocumentReview r join fetch r.document d left join fetch d.type
			where r.reviewedAt >= :from and r.reviewedAt < :to
			and (r.reviewNote like 'APPROVE%' or r.reviewNote like 'REJECT%' or r.reviewNote like 'REQUEST_INFO%')""")
	List<DocumentReview> findDecisionsBetween(java.time.LocalDateTime from, java.time.LocalDateTime to);

	List<DocumentReview> findByDocumentIdIn(java.util.Collection<Long> documentIds);
}
