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

	/** Persist the complete assessment in bounded chunks using the existing log schema. */
	@Transactional
	public void recordAssessment(Long documentId, com.example.invoice.dto.document.DocumentAssessment assessment) {
		try {
			String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(assessment);
			append(documentId, "AI_ASSESSMENT", "BEGIN", "SUCCESS", null, null, "version=1");
			for (int offset = 0; offset < json.length();) {
				int end = Math.min(offset + 200, json.length());
				if (end < json.length() && Character.isHighSurrogate(json.charAt(end - 1))) end--;
				append(documentId, "AI_ASSESSMENT", "PART", "SUCCESS", null, null,
						json.substring(offset, end));
				offset = end;
			}
			append(documentId, "AI_ASSESSMENT", "END", "SUCCESS", null, null, "version=1");
		} catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
			throw new IllegalStateException("Không thể lưu đánh giá OCR/AI", exception);
		}
	}

	@Transactional(readOnly = true)
	public com.example.invoice.dto.document.DocumentAssessment assessment(Long documentId) {
		Document document = documentService.load(documentId); // Enforce existing tenant/user scope.
		StringBuilder json = new StringBuilder();
		com.example.invoice.dto.document.DocumentAssessment latest = null;
		boolean collecting = false;
		var logs = new java.util.ArrayList<>(findByDocumentId(documentId));
		logs.sort(java.util.Comparator.comparing(ProcessingLog::getId));
		for (ProcessingLog entry : logs) {
			if ("AI_DOCUMENT".equals(entry.getProcessType()) && "START".equals(entry.getAgentStep())) {
				latest = null; collecting = false; json.setLength(0);
			}
			if (!"AI_ASSESSMENT".equals(entry.getProcessType())) continue;
			if ("BEGIN".equals(entry.getAgentStep())) { collecting = true; json.setLength(0); }
			else if (collecting && "PART".equals(entry.getAgentStep())) json.append(entry.getMessage());
			else if (collecting && "END".equals(entry.getAgentStep())) {
				try { latest = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json.toString(),
						com.example.invoice.dto.document.DocumentAssessment.class); }
				catch (com.fasterxml.jackson.core.JsonProcessingException exception) { latest = null; }
				collecting = false;
			}
		}
		if (latest != null && document.getStatus() != com.example.invoice.entity.DocumentStatus.FAILED
				&& document.getStatus() != com.example.invoice.entity.DocumentStatus.PROCESSING) {
			if (document.getStatus() == com.example.invoice.entity.DocumentStatus.NEED_REVIEW && latest.reviewReasons().isEmpty())
				return new com.example.invoice.dto.document.DocumentAssessment(latest.ocrConfidence(), latest.extractionConfidence(),
						latest.classificationConfidence(), latest.validationStatus(), latest.warnings(), java.util.List.of(
						"WORKFLOW_REVIEW_REQUIRED: Trạng thái nghiệp vụ yêu cầu kiểm tra; đánh giá OCR/AI không thay thế quyết định workflow"));
			return latest;
		}
		var unknown = new com.example.invoice.dto.document.DocumentAssessment.Confidence(null, null, "UNAVAILABLE");
		String status = document.getStatus().name();
		String reason = switch (document.getStatus()) {
			case NEED_REVIEW -> "LEGACY_ASSESSMENT_UNAVAILABLE: Chứng từ cần kiểm tra nhưng chưa có đánh giá đầy đủ của lần xử lý hiện tại";
			case FAILED -> "PROCESSING_FAILED: Xử lý chưa hoàn tất; kết quả cũ không được coi là đánh giá hiện tại";
			default -> "ASSESSMENT_UNAVAILABLE: Chưa có đánh giá của lần xử lý hiện tại";
		};
		return new com.example.invoice.dto.document.DocumentAssessment(unknown, unknown, unknown,
				status.equals("FAILED") ? "FAILED" : "UNKNOWN", java.util.List.of(), java.util.List.of(reason));
	}

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
