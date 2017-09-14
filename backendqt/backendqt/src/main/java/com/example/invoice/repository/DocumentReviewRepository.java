package com.example.invoice.repository;

import com.example.invoice.entity.DocumentReview;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentReviewRepository extends JpaRepository<DocumentReview, Long> {
	List<DocumentReview> findByDocumentId(Long documentId);
}
