package com.example.invoice.service;

import com.example.invoice.ai.AiDocumentPromptFactory;
import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.ai.AiDocumentResultValidator.ValidatedAiDocumentResult;
import com.example.invoice.ai.AiProcessingService;
import com.example.invoice.dto.ai.AiDocumentProcessingResponse;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.DocumentTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Coordinates MinIO, remote AI, validation and persistence without a remote call inside a DB transaction. */
@Service
@RequiredArgsConstructor
public class DocumentAiProcessingService {
	private static final Logger log = LoggerFactory.getLogger(DocumentAiProcessingService.class);
	private final DocumentService documentService;
	private final DocumentTypeRepository documentTypeRepository;
	private final AiProcessingService aiProcessingService;
	private final AiDocumentResultValidator resultValidator;
	private final DocumentAiResultPersistenceService persistenceService;
	private final ProcessingLogService processingLogService;

	public AiDocumentProcessingResponse process(Long documentId, boolean reprocess) {
		Document document = documentService.load(documentId);
		if (document.getStatus() == DocumentStatus.PROCESSING) {
			throw new BadRequestException("Document is already being processed");
		}
		if (!reprocess && document.getStatus() != DocumentStatus.UPLOADED) {
			throw new BadRequestException("Document has already been processed; use reprocess to run AI again");
		}
		if (!isSupportedImage(document.getFileType())) {
			if ("application/pdf".equalsIgnoreCase(document.getFileType())) {
				throw new BadRequestException("PDF_AI_PROCESSING_PENDING: PDF to image conversion is not implemented");
			}
			throw new BadRequestException("Only image/jpeg and image/png are supported for AI document processing");
		}

		List<DocumentType> types = documentTypeRepository.findByActiveTrue();
		if (types.isEmpty()) {
			throw new BadRequestException("No active document types are configured for AI classification");
		}
		List<String> typeCodes = types.stream().map(DocumentType::getCode).toList();
		Set<String> allowedTypes = typeCodes.stream().collect(Collectors.toUnmodifiableSet());
		byte[] bytes = documentService.loadFileBytes(documentId);
		if (bytes.length == 0) {
			throw new BadRequestException("AI document content loaded from MinIO is empty");
		}
		if (document.getFileSize() != null && document.getFileSize() != bytes.length) {
			throw new BadRequestException("AI document content size does not match the stored document metadata");
		}
		log.info("AI document input documentId={} mimeType={} storedBytes={} minioBytes={} allowedTypeCount={}",
				documentId, document.getFileType(), document.getFileSize(), bytes.length, typeCodes.size());
		documentService.updateProcessingStatus(documentId, DocumentStatus.PROCESSING);
		processingLogService.append(documentId, "AI_DOCUMENT", "START", "PROCESSING", null, null,
				reprocess ? "AI reprocess requested" : "AI process requested");

		try {
			AiDocumentRequest request = new AiDocumentRequest(document.getOriginalFileName(), document.getFileType(), bytes,
					typeCodes, AiDocumentPromptFactory.create(typeCodes));
			AiDocumentResult result = aiProcessingService.analyzeDocument(request);
			ValidatedAiDocumentResult validated = resultValidator.validate(result, allowedTypes);
			boolean requiresReview = persistenceService.persist(documentId, validated);
			DocumentStatus status = requiresReview ? DocumentStatus.NEED_REVIEW : DocumentStatus.PROCESSED;
			return new AiDocumentProcessingResponse(documentId, status, requiresReview, validated.warnings());
		} catch (RuntimeException exception) {
			documentService.updateProcessingStatus(documentId, DocumentStatus.FAILED);
			processingLogService.append(documentId, "AI_DOCUMENT", "FAILED", "FAILED", BigDecimal.ZERO, null,
					sanitize(exception.getMessage()));
			throw exception;
		}
	}

	private boolean isSupportedImage(String contentType) {
		return "image/jpeg".equalsIgnoreCase(contentType) || "image/png".equalsIgnoreCase(contentType);
	}

	private String sanitize(String message) {
		return message == null || message.isBlank() ? "AI document processing failed" : message.substring(0, Math.min(message.length(), 500));
	}
}
