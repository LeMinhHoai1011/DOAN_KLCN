package com.example.invoice.repository;

import com.example.invoice.entity.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {
    Optional<DocumentType> findByCode(String code);
    List<DocumentType> findByActiveTrue();

	@Query("select type, count(document.id) from DocumentType type left join Document document on document.type = type or (document.type is null and document.documentType = type.code) group by type order by type.code")
	List<Object[]> findAllWithDocumentCount();
}
