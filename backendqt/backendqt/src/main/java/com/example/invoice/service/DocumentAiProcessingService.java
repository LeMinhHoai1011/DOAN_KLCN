package com.example.invoice.service;

import com.example.invoice.ai.AiDocumentPromptFactory;
import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.ai.AiProcessingService;
import com.example.invoice.dto.ai.AiDocumentProcessingResponse;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiTextRequest;
import com.example.invoice.entity.Company;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Single orchestration path: storage -> text/OCR -> AI -> validation -> persistence. */
@Service
@RequiredArgsConstructor
public class DocumentAiProcessingService {
	private final DocumentService documentService;
	private final DocumentTypeRepository documentTypeRepository;
	private final AccountingCategoryRepository accountingCategoryRepository;
	private final DocumentTextExtractionService textExtractionService;
	private final OCRResultService ocrResultService;
	private final AiProcessingService aiProcessingService;
	private final AiDocumentResultValidator resultValidator;
	private final DocumentAiResultPersistenceService persistenceService;
	private final ProcessingLogService processingLogService;
	private final PdfImageConversionService pdfImageConversionService;

	public AiDocumentProcessingResponse process(Long documentId, boolean reprocess) {
		Document document = documentService.load(documentId);
		if (document.getStatus() == DocumentStatus.PROCESSING) throw new BadRequestException("Document is already being processed");
		if (!reprocess && document.getStatus() != DocumentStatus.UPLOADED)
			throw new BadRequestException("Document has already been processed; use reprocess to run AI again");
		documentService.updateProcessingStatus(documentId, DocumentStatus.PROCESSING);
		try {
			processingLogService.append(documentId, "DOCUMENT_PIPELINE", "START", "PROCESSING", null, null,
					reprocess ? "FULL_REPROCESS" : "PROCESS");
			validateContentType(document.getFileType());
			List<DocumentType> types = documentTypeRepository.findByActiveTrue();
			if (types.isEmpty()) throw new BadRequestException("AI_EXTRACTION_FAILED: no active document types configured");
			List<String> typeCodes = types.stream().map(DocumentType::getCode).toList();
			Company company = document.getCompany();
			List<String> categoryCodes = company == null ? List.of() : accountingCategoryRepository.findByCompanyId(company.getId())
					.stream().filter(category -> category.isActive()).map(category -> category.getCategoryCode()).toList();
			byte[] bytes = documentService.loadFileBytes(documentId);
			DocumentTextExtractionResult extraction = textExtractionService.extract(bytes, document.getFileType());
			ocrResultService.persistExtraction(documentId, extraction);
			processingLogService.append(documentId, "OCR", extraction.sourceType(),
					extraction.visionFallbackRecommended() ? "WARNING" : "SUCCESS", extraction.confidence(),
					extraction.durationMs(), String.join("; ", extraction.warnings()), extraction.engine(), extraction.engineVersion());
			String prompt = AiDocumentPromptFactory.create(typeCodes, categoryCodes,
					company == null ? null : company.getCompanyName(), company == null ? null : company.getTaxCode());
			AiDocumentResult aiResult = extraction.visionFallbackRecommended()
					? analyzeVisionFallback(document, bytes, prompt, extraction, typeCodes)
					: aiProcessingService.analyzeTextDocument(new AiTextRequest(document.getOriginalFileName(), extraction.text(), prompt));
			processingLogService.append(documentId, "AI_DOCUMENT", extraction.visionFallbackRecommended() ? "VISION_FALLBACK" : "TEXT_EXTRACTION",
					"SUCCESS", aiResult.classificationConfidence(), aiResult.durationMs(), null, aiResult.provider(), aiResult.model());
			var validated = resultValidator.validate(aiResult, Set.copyOf(typeCodes));
			boolean review = persistenceService.persist(documentId, validated);
			List<String> warnings = java.util.stream.Stream.concat(extraction.warnings().stream(), validated.warnings().stream()).distinct().toList();
			if (!extraction.warnings().isEmpty() && !review) {
				documentService.updateProcessingStatus(documentId, DocumentStatus.NEED_REVIEW);
				review = true;
			}
			return new AiDocumentProcessingResponse(documentId, review ? DocumentStatus.NEED_REVIEW : DocumentStatus.PROCESSED, review, warnings, null);
		} catch (RuntimeException exception) {
			documentService.updateProcessingStatus(documentId, DocumentStatus.FAILED);
			String code = errorCode(exception);
			processingLogService.append(documentId, "DOCUMENT_PIPELINE", code, "FAILED", BigDecimal.ZERO, null, sanitize(exception.getMessage()));
			throw exception;
		}
	}

	AiDocumentResult analyzeVisionFallback(Document document, byte[] bytes, String prompt,
			DocumentTextExtractionResult extraction, List<String> typeCodes) {
		String sharedContext = "\n\nOCR CONTEXT FROM ALL PAGES (may contain errors; use the image to verify, never invent):\n"
				+ extraction.text();
		if (!"application/pdf".equalsIgnoreCase(document.getFileType())) {
			return aiProcessingService.analyzeDocument(new AiDocumentRequest(document.getOriginalFileName(),
					document.getFileType(), bytes, typeCodes, prompt + sharedContext));
		}

		List<DocumentTextExtractionResult.PageText> problematic = extraction.pages().stream()
				.filter(DocumentTextExtractionResult.PageText::requiresVisionFallback).toList();
		if (problematic.isEmpty()) {
			throw new BadRequestException("OCR_LOW_CONFIDENCE: fallback requested without a problematic PDF page");
		}
		java.util.Map<Integer, PdfPageImage> rendered = pdfImageConversionService.convert(bytes).stream()
				.collect(java.util.stream.Collectors.toMap(PdfPageImage::pageNumber, page -> page));
		List<AiDocumentResult> pageResults = new java.util.ArrayList<>();
		for (DocumentTextExtractionResult.PageText page : problematic) {
			PdfPageImage image = rendered.get(page.pageNumber());
			if (image == null) throw new BadRequestException("PDF_EXTRACTION_FAILED: missing rendered page " + page.pageNumber());
			String pagePrompt = prompt + "\n\nVISION FALLBACK TARGET: PAGE " + page.pageNumber()
					+ ". Extract only evidence visible on this page; the full OCR context follows." + sharedContext;
			pageResults.add(aiProcessingService.analyzeDocument(new AiDocumentRequest(
					document.getOriginalFileName() + "#page-" + page.pageNumber(), image.contentType(), image.bytes(), typeCodes, pagePrompt)));
		}
		if (pageResults.size() == 1) return pageResults.getFirst();

		StringBuilder pageJson = new StringBuilder(extraction.text());
		for (int index = 0; index < pageResults.size(); index++) {
			pageJson.append("\n\n[VISION PAGE ").append(problematic.get(index).pageNumber()).append("]\n")
					.append(pageResults.get(index).rawResponse());
		}
		return aiProcessingService.analyzeTextDocument(new AiTextRequest(document.getOriginalFileName(), pageJson.toString(),
				prompt + "\nConsolidate the OCR text and per-page Vision results into one document. Deduplicate invoice items."));
	}

	private void validateContentType(String type) {
		if (!"application/pdf".equalsIgnoreCase(type) && !"image/jpeg".equalsIgnoreCase(type) && !"image/png".equalsIgnoreCase(type))
			throw new BadRequestException("OCR_FAILED: unsupported content type");
	}

	private String errorCode(RuntimeException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage().toUpperCase(java.util.Locale.ROOT);
		for (String code : List.of("PDF_EXTRACTION_FAILED", "OCR_LOW_CONFIDENCE", "OCR_FAILED", "AI_TIMEOUT",
				"AI_INVALID_RESPONSE", "AI_EXTRACTION_FAILED", "OLLAMA_CONNECT_TIMEOUT", "OLLAMA_READ_TIMEOUT",
				"OLLAMA_CALL_TIMEOUT", "OLLAMA_UNREACHABLE", "OLLAMA_INVALID_RESPONSE", "OLLAMA_EMPTY_RESPONSE",
				"OLLAMA_MODEL_NOT_FOUND", "OLLAMA_TOKEN_LIMIT", "VALIDATION_FAILED", "PERSISTENCE_FAILED"))
			if (message.contains(code)) return code;
		if (message.contains("OLLAMA") || message.contains("AI_")) return "AI_PROVIDER_ERROR";
		return "PERSISTENCE_FAILED";
	}

	private String sanitize(String message) {
		String value = message == null || message.isBlank() ? "Document processing failed" : message;
		return value.substring(0, Math.min(500, value.length()));
	}
}
