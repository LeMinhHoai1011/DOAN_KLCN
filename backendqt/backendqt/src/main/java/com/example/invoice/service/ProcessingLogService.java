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
		return append(documentId, processType, agentStep, status, confidence, executionTime, message, null, null);
	}

	@Transactional
	public ProcessingLog append(Long documentId, String processType, String agentStep, String status,
			BigDecimal confidence, Long executionTime, String message, String modelName, String modelVersion) {
		Document document = documentService.load(documentId);
		ProcessingLog log = new ProcessingLog();
		log.setDocument(document);
		log.setProcessType(limit(processType));
		log.setAgentStep(limit(agentStep));
		log.setStatus(limit(status));
		log.setConfidence(confidence);
		log.setExecutionTime(executionTime);
		log.setMessage(limit(message));
		log.setModelName(limit(modelName));
		log.setModelVersion(limit(modelVersion));
		return processingLogRepository.save(log);
	}

	private String limit(String value) {
		if (value == null || value.length() <= 255) return value;
		return value.substring(0, 255);
	}
}
