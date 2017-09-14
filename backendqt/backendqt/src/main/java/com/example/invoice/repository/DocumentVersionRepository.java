package com.example.invoice.repository;

import com.example.invoice.entity.DocumentVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
	List<DocumentVersion> findByDocumentId(Long documentId);
}
