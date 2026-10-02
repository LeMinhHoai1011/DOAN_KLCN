package com.example.invoice.service;

import com.example.invoice.dto.document.DocumentResponse;
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

	public DocumentResponse uploadAndProcess(MultipartFile file, Authentication authentication) {
		DocumentResponse uploaded = documentService.createFromUpload(file, authentication);
		try {
			documentAiProcessingService.process(uploaded.id(), false);
		} catch (RuntimeException exception) {
			// The processor records FAILED and a ProcessingLog; preserve the upload for reprocess.
			log.warn("Automatic AI processing failed for documentId={}: {}", uploaded.id(), exception.getMessage());
			if (documentService.findById(uploaded.id()).status() != DocumentStatus.FAILED) {
				documentService.updateProcessingStatus(uploaded.id(), DocumentStatus.FAILED);
				processingLogService.append(uploaded.id(), "AI_DOCUMENT", "FAILED", "FAILED", BigDecimal.ZERO,
						null, sanitize(exception.getMessage()));
			}
		}
		return documentService.findById(uploaded.id());
	}

	private String sanitize(String message) {
		return message == null || message.isBlank() ? "AI document processing failed"
				: message.substring(0, Math.min(message.length(), 500));
	}
}
