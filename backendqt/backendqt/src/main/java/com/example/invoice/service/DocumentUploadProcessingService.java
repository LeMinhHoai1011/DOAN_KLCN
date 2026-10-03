package com.example.invoice.service;

import com.example.invoice.dto.document.DocumentResponse;
import com.example.invoice.dto.document.DocumentUploadResponse;
import com.example.invoice.entity.DocumentStatus;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Upload boundary that commits file/document persistence before remote AI work. */
@Service
@RequiredArgsConstructor
public class DocumentUploadProcessingService {
	private static final Logger log = LoggerFactory.getLogger(DocumentUploadProcessingService.class);
	private final DocumentService documentService;
	private final DocumentAiProcessingService documentAiProcessingService;
	private final ProcessingLogService processingLogService;

	public DocumentUploadResponse uploadAndProcess(MultipartFile file, Authentication authentication) {
		DocumentResponse uploaded = documentService.createFromUpload(file, authentication);
		RuntimeException processingFailure = null;
		try {
			documentAiProcessingService.process(uploaded.id(), false);
		} catch (RuntimeException exception) {
			processingFailure = exception;
			// The processor records FAILED and a ProcessingLog; preserve the upload for reprocess.
			log.warn("Automatic AI processing failed for documentId={}: {}", uploaded.id(), exception.getMessage());
			try {
				if (documentService.findById(uploaded.id()).status() != DocumentStatus.FAILED) {
					documentService.updateProcessingStatus(uploaded.id(), DocumentStatus.FAILED);
					processingLogService.append(uploaded.id(), "DOCUMENT_PIPELINE", "FAILED", "FAILED", BigDecimal.ZERO,
							null, sanitize(exception.getMessage()));
				}
			} catch (RuntimeException recoveryException) {
				log.error("Could not record automatic processing failure for documentId={}", uploaded.id(), recoveryException);
			}
		}
		DocumentResponse current = documentService.findById(uploaded.id());
		if (processingFailure == null) return new DocumentUploadResponse(true, current.id(), current.status(), current.reviewStatus(),
				"Tải lên và xử lý chứng từ thành công", null);
		String code = errorCode(processingFailure);
		return new DocumentUploadResponse(false, current.id(), current.status(), current.reviewStatus(),
				"Đã tải lên chứng từ nhưng xử lý tự động thất bại",
				new DocumentUploadResponse.UploadError(code, sanitize(processingFailure.getMessage()), isRetryable(code)));
	}

	private String errorCode(RuntimeException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage().toUpperCase(java.util.Locale.ROOT);
		for (String code : java.util.List.of("OCR_LANGUAGE_DATA_MISSING", "OCR_FAILED", "AI_CONTEXT_EXCEEDED",
				"OLLAMA_TIMEOUT", "OLLAMA_EMPTY_RESPONSE", "OLLAMA_INVALID_JSON", "OLLAMA_TOKEN_LIMIT",
				"OLLAMA_MODEL_NOT_FOUND", "AI_CONNECTION_ERROR", "AI_INVALID_RESPONSE", "PERSISTENCE_FAILED"))
			if (message.contains(code)) return code;
		return "AI_PROCESSING_FAILED";
	}

	private boolean isRetryable(String code) {
		return !"PERSISTENCE_FAILED".equals(code) && !"OCR_LANGUAGE_DATA_MISSING".equals(code);
	}

	private String sanitize(String message) {
		return message == null || message.isBlank() ? "Xử lý chứng từ bằng AI thất bại"
				: message.substring(0, Math.min(message.length(), 255));
	}
}
