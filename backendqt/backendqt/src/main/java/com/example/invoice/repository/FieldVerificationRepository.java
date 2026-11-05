package com.example.invoice.repository;

import com.example.invoice.entity.FieldVerification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldVerificationRepository extends JpaRepository<FieldVerification, Long> {
	List<FieldVerification> findByDocumentIdOrderByIdAsc(Long documentId);

	@Modifying(flushAutomatically = true)
	@Query("delete from FieldVerification v where v.document.id = :documentId")
	int deleteByDocumentId(@Param("documentId") Long documentId);
}
