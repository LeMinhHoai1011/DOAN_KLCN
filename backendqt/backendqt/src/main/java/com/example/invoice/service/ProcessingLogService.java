package com.example.invoice.service;

import com.example.invoice.entity.Document;
import com.example.invoice.entity.ProcessingLog;
import com.example.invoice.repository.ProcessingLogRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessingLogService {
	private final ProcessingLogRepository processingLogRepository;
	private final DocumentService documentService;

	public List<ProcessingLog> findByDocumentId(Long documentId) {
		return processingLogRepository.findByDocumentIdOrderByCreatedAtAsc(documentId);
	}

	@Transactional
	public ProcessingLog append(Long documentId, String processType, String agentStep, String status,
			BigDecimal confidence, Long executionTime, String message) {
		Document document = documentService.load(documentId);
		ProcessingLog log = new ProcessingLog();
		log.setDocument(document);
		log.setProcessType(processType);
		log.setAgentStep(agentStep);
		log.setStatus(status);
		log.setConfidence(confidence);
		log.setExecutionTime(executionTime);
		log.setMessage(message);
		return processingLogRepository.save(log);
	}
}
