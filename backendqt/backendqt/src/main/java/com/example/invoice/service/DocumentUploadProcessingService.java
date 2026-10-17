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
		DocumentResponse uploaded;
        try { uploaded = documentService.createFromUpload(file, authentication); }
        catch (com.example.invoice.exception.PipelineException exception) { throw exception; }
        catch (com.example.invoice.exception.BadRequestException exception) {
            throw new com.example.invoice.exception.PipelineException(PipelineFailures.create("INVALID_FILE", "FILE_VALIDATION", false, 0), 400);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new com.example.invoice.exception.PipelineException(PipelineFailures.classify(exception, "UPLOAD", 0), 500);
        } catch (org.springframework.security.core.AuthenticationException | org.springframework.security.access.AccessDeniedException | com.example.invoice.exception.ResourceNotFoundException exception) { throw exception; }
        catch (RuntimeException exception) { throw new com.example.invoice.exception.PipelineException(PipelineFailures.classify(exception, "UPLOAD", 0), 500); }
		RuntimeException processingFailure = null;
		try {
			documentAiProcessingService.process(uploaded.id(), false);
		} catch (RuntimeException exception) {
			processingFailure = exception;
            log.warn("Automatic processing failed documentId={} exceptionClass={}", uploaded.id(), exception.getClass().getName());
		}
		DocumentResponse current = documentService.findById(uploaded.id());
		if (processingFailure == null) return new DocumentUploadResponse(true, current.id(), current.status(), current.reviewStatus(),
				current.documentTypeCode(), current.documentTypeName(), current.aiConfidence(),
				"Tải lên và xử lý chứng từ thành công", null);
		var failure = PipelineFailures.classify(processingFailure, "AI_EXTRACTION", processingFailure instanceof com.example.invoice.exception.PipelineException p ? p.failure().attemptCount() : 0);
		return new DocumentUploadResponse(false, current.id(), current.status(), current.reviewStatus(),
				current.documentTypeCode(), current.documentTypeName(), current.aiConfidence(),
				"Đã tải lên chứng từ nhưng xử lý tự động thất bại",
				new DocumentUploadResponse.UploadError(failure));
	}

}
