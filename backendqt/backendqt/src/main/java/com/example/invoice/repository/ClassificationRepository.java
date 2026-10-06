package com.example.invoice.repository;

import com.example.invoice.entity.Classification;
import com.example.invoice.entity.ClassificationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClassificationRepository extends JpaRepository<Classification, Long> {
	Optional<Classification> findFirstByDocumentIdOrderByCreatedAtDesc(Long documentId);

	long countByStatus(ClassificationStatus status);
	long countByDocumentCompanyIdAndStatus(Long companyId, ClassificationStatus status);
	long countByDocumentUploadedByIdAndStatus(Long uploadedById, ClassificationStatus status);

	@Query("select count(distinct c.document.id) from Classification c where c.status in :statuses and c.createdAt = (select max(latest.createdAt) from Classification latest where latest.document.id = c.document.id)")
	long countCurrentDocumentsByStatusIn(@Param("statuses") java.util.Collection<ClassificationStatus> statuses);
	@Query("select count(distinct c.document.id) from Classification c where c.document.company.id = :companyId and c.status in :statuses and c.createdAt = (select max(latest.createdAt) from Classification latest where latest.document.id = c.document.id)")
	long countCurrentDocumentsByCompanyAndStatusIn(@Param("companyId") Long companyId, @Param("statuses") java.util.Collection<ClassificationStatus> statuses);
	@Query("select count(distinct c.document.id) from Classification c where c.document.uploadedBy.id = :userId and c.status in :statuses and c.createdAt = (select max(latest.createdAt) from Classification latest where latest.document.id = c.document.id)")
	long countCurrentDocumentsByUploaderAndStatusIn(@Param("userId") Long userId, @Param("statuses") java.util.Collection<ClassificationStatus> statuses);
}
