package com.example.invoice.repository;

import com.example.invoice.entity.OCRResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OCRResultRepository extends JpaRepository<OCRResult, Long> {
	Optional<OCRResult> findFirstByDocumentIdOrderByProcessedAtDesc(Long documentId);
}
