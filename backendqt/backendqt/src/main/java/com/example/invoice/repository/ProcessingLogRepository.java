package com.example.invoice.repository;

import com.example.invoice.entity.ProcessingLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessingLogRepository extends JpaRepository<ProcessingLog, Long> {
	List<ProcessingLog> findByDocumentIdOrderByCreatedAtAsc(Long documentId);
}
