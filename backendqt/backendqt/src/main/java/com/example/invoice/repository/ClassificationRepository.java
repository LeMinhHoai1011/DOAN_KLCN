package com.example.invoice.repository;

import com.example.invoice.entity.Classification;
import com.example.invoice.entity.ClassificationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassificationRepository extends JpaRepository<Classification, Long> {
	Optional<Classification> findFirstByDocumentIdOrderByCreatedAtDesc(Long documentId);

	long countByStatus(ClassificationStatus status);
	long countByDocumentCompanyIdAndStatus(Long companyId, ClassificationStatus status);
	long countByDocumentUploadedByIdAndStatus(Long uploadedById, ClassificationStatus status);
}
