package com.example.invoice.repository;

import com.example.invoice.entity.Invoice;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findAllByDocumentCompanyId(Long companyId);
    Optional<Invoice> findByIdAndDocumentCompanyId(Long id, Long companyId);
    Optional<Invoice> findByDocumentIdAndDocumentCompanyId(Long documentId, Long companyId);
	List<Invoice> findAllByDocumentUploadedById(Long uploadedById);
	Optional<Invoice> findByIdAndDocumentUploadedById(Long id, Long uploadedById);
	Optional<Invoice> findByDocumentIdAndDocumentUploadedById(Long documentId, Long uploadedById);
	long countByDocumentCompanyId(Long companyId);
	long countByDocumentUploadedById(Long uploadedById);
	Optional<Invoice> findByDocumentId(Long documentId);
	@Modifying(flushAutomatically = true)
	@Query("delete from Invoice invoice where invoice.document.id = :documentId and invoice.aiGenerated = true")
	int deleteAiGeneratedByDocumentId(@Param("documentId") Long documentId);
}

