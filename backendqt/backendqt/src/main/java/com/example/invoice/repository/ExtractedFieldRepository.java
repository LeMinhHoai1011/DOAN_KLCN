package com.example.invoice.repository;

import com.example.invoice.entity.ExtractedField;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, Long> {
	List<ExtractedField> findByInvoiceId(Long invoiceId);
	List<ExtractedField> findByDocumentId(Long documentId);
	@Modifying(flushAutomatically = true)
	@Query("delete from ExtractedField field where field.document.id = :documentId and field.source = :source")
	int deleteByDocumentIdAndSource(@Param("documentId") Long documentId, @Param("source") String source);
}
