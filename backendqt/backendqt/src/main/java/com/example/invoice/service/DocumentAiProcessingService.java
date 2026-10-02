package com.example.invoice.service;

import com.example.invoice.ai.AiDocumentPromptFactory;
import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.ai.AiDocumentResultValidator.ValidatedAiDocumentResult;
import com.example.invoice.ai.AiProcessingService;
import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiDocumentProcessingResponse;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.AiTextRequest;
import com.example.invoice.dto.ai.ImagePreprocessingMetadata;
import com.example.invoice.entity.Company;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Coordinates storage, image/PDF OCR, AI classification, validation and persistence. */
@Service
public class DocumentAiProcessingService {
	private static final Logger log = LoggerFactory.getLogger(DocumentAiProcessingService.class);

	private final DocumentService documentService;
	private final DocumentTypeRepository documentTypeRepository;
	private final AccountingCategoryRepository accountingCategoryRepository;
	private final AiProcessingService aiProcessingService;
	private final AiDocumentResultValidator resultValidator;
	private final DocumentAiResultPersistenceService persistenceService;
	private final ProcessingLogService processingLogService;
	private final ImagePreprocessingService imagePreprocessingService;
	private final PdfImageConversionService pdfImageConversionService;
	private final Tess4jOcrService tess4jOcrService;
	private final AiProperties aiProperties;

	@Autowired
	public DocumentAiProcessingService(DocumentService documentService, DocumentTypeRepository documentTypeRepository,
			AccountingCategoryRepository accountingCategoryRepository, AiProcessingService aiProcessingService,
			AiDocumentResultValidator resultValidator, DocumentAiResultPersistenceService persistenceService,
			ProcessingLogService processingLogService, ImagePreprocessingService imagePreprocessingService,
			PdfImageConversionService pdfImageConversionService, Tess4jOcrService tess4jOcrService,
			AiProperties aiProperties) {
		this.documentService = documentService;
		this.documentTypeRepository = documentTypeRepository;
		this.accountingCategoryRepository = accountingCategoryRepository;
		this.aiProcessingService = aiProcessingService;
		this.resultValidator = resultValidator;
		this.persistenceService = persistenceService;
		this.processingLogService = processingLogService;
		this.imagePreprocessingService = imagePreprocessingService;
		this.pdfImageConversionService = pdfImageConversionService;
		this.tess4jOcrService = tess4jOcrService;
		this.aiProperties = aiProperties;
	}

	/** Compatibility constructor retained for the text-first PDF fallback tests. */
	DocumentAiProcessingService(DocumentService documentService, DocumentTypeRepository documentTypeRepository,
			AccountingCategoryRepository accountingCategoryRepository, DocumentTextExtractionService ignoredExtractionService,
			OCRResultService ignoredOcrResultService, AiProcessingService aiProcessingService,
			AiDocumentResultValidator resultValidator, DocumentAiResultPersistenceService persistenceService,
			ProcessingLogService processingLogService, PdfImageConversionService pdfImageConversionService) {
		this(documentService, documentTypeRepository, accountingCategoryRepository, aiProcessingService, resultValidator,
				persistenceService, processingLogService, null, pdfImageConversionService, null, new AiProperties());
	}

	public AiDocumentProcessingResponse process(Long documentId, boolean reprocess) {
		Document document = documentService.load(documentId);
		if (document.getStatus() == DocumentStatus.PROCESSING)
			throw new BadRequestException("Document is already being processed");
		if (!reprocess && document.getStatus() != DocumentStatus.UPLOADED)
			throw new BadRequestException("Document has already been processed; use reprocess to run AI again");
		if (!isSupportedImage(document.getFileType()) && !isPdf(document.getFileType()))
			throw new BadRequestException("Only application/pdf, image/jpeg and image/png are supported");

		List<DocumentType> types = documentTypeRepository.findByActiveTrue();
		if (types.isEmpty()) throw new BadRequestException("No active document types are configured for AI classification");
		List<String> typeCodes = types.stream().map(DocumentType::getCode).toList();
		Set<String> allowedTypes = Set.copyOf(typeCodes);
		Company company = document.getCompany();
		List<String> categoryCodes = company == null ? List.of()
				: accountingCategoryRepository.findByCompanyId(company.getId()).stream()
						.filter(com.example.invoice.entity.AccountingCategory::isActive)
						.map(com.example.invoice.entity.AccountingCategory::getCategoryCode).toList();

		byte[] bytes = documentService.loadFileBytes(documentId);
		if (bytes.length == 0) throw new BadRequestException("Document content loaded from MinIO is empty");
		if (document.getFileSize() != null && document.getFileSize() != bytes.length)
			throw new BadRequestException("Document content size does not match stored metadata");

		documentService.updateProcessingStatus(documentId, DocumentStatus.PROCESSING);
		processingLogService.append(documentId, "AI_DOCUMENT", "START", "PROCESSING", null, null,
				reprocess ? "AI reprocess requested" : "AI process requested");

		try {
			return isPdf(document.getFileType())
					? processPdf(documentId, document, bytes, typeCodes, categoryCodes, allowedTypes, company)
					: processImage(documentId, document, bytes, typeCodes, categoryCodes, allowedTypes, company);
		} catch (RuntimeException exception) {
			documentService.updateProcessingStatus(documentId, DocumentStatus.FAILED);
			processingLogService.append(documentId, "DOCUMENT_PIPELINE", errorCode(exception), "FAILED",
					BigDecimal.ZERO, null, sanitize(exception.getMessage()));
			throw exception;
		}
	}

	private AiDocumentProcessingResponse processPdf(Long documentId, Document document, byte[] bytes,
			List<String> typeCodes, List<String> categoryCodes, Set<String> allowedTypes, Company company) {
		List<PageAnalysis> analyzedPages = analyzePdfPages(document.getOriginalFileName(), bytes, typeCodes, categoryCodes, company);
		AiDocumentResult result = resolveCompanyRole(aggregatePageResults(
				analyzedPages.stream().map(PageAnalysis::aiResult).toList()), company);
		OcrDocumentResult ocr = aggregateOcr(analyzedPages.stream().map(PageAnalysis::ocr).toList());
		ValidatedAiDocumentResult validated = resultValidator.validate(result, allowedTypes);
		boolean requiresReview = persistenceService.persist(documentId, validated, ocr);
		List<String> warnings = new ArrayList<>(validated.warnings());
		warnings.addAll(ocr.warnings());
		return response(documentId, requiresReview, warnings, null);
	}

	private AiDocumentProcessingResponse processImage(Long documentId, Document document, byte[] originalBytes,
			List<String> typeCodes, List<String> categoryCodes, Set<String> allowedTypes, Company company) {
		byte[] bytes = originalBytes;
		String contentType = document.getFileType();
		List<String> warnings = new ArrayList<>();
		ImagePreprocessingResult preprocessing = null;
		try {
			preprocessing = imagePreprocessingService.preprocess(bytes, contentType);
			if (preprocessing.applied()) {
				String objectKey = documentService.storeProcessedImage(documentId, preprocessing.bytes(), preprocessing.contentType());
				bytes = preprocessing.bytes();
				contentType = preprocessing.contentType();
				processingLogService.append(documentId, "IMAGE_PREPROCESSING", "NORMALIZE", "SUCCESS", null,
						preprocessing.durationMs(), "angle=%.2f; original=%dx%d; processed=%dx%d; objectKey=%s".formatted(
								preprocessing.detectedAngleDegrees(), preprocessing.originalWidth(), preprocessing.originalHeight(),
								preprocessing.processedWidth(), preprocessing.processedHeight(), objectKey));
			}
		} catch (RuntimeException exception) {
			warnings.add("Image preprocessing failed; original image was sent to OCR and AI");
			processingLogService.append(documentId, "IMAGE_PREPROCESSING", "NORMALIZE", "WARNING", null, null,
					"Preprocessing failed: " + sanitize(exception.getMessage()));
		}

		log.info("AI input documentId={} mimeType={} bytes={} allowedTypeCount={}",
				documentId, contentType, bytes.length, typeCodes.size());
		OcrPageResult pageOcr = tess4jOcrService.recognize(bytes, 1);
		logOcr(documentId, pageOcr);
		OcrDocumentResult ocr = aggregateOcr(List.of(pageOcr));
		AiDocumentResult result = resolveCompanyRole(analyzeSingleImage(document.getOriginalFileName(), contentType,
				bytes, typeCodes, categoryCodes, company, pageOcr), company);
		ValidatedAiDocumentResult validated = resultValidator.validate(result, allowedTypes);
		boolean requiresReview = persistenceService.persist(documentId, validated, ocr);
		warnings.addAll(validated.warnings());
		warnings.addAll(ocr.warnings());
		ImagePreprocessingMetadata metadata = preprocessing == null ? null : new ImagePreprocessingMetadata(
				preprocessing.applied(), preprocessing.detectedAngleDegrees(), preprocessing.originalWidth(), preprocessing.originalHeight(),
				preprocessing.processedWidth(), preprocessing.processedHeight(), preprocessing.durationMs(), preprocessing.warning());
		return response(documentId, requiresReview, warnings, metadata);
	}

	private AiDocumentProcessingResponse response(Long documentId, boolean requiresReview, List<String> warnings,
			ImagePreprocessingMetadata metadata) {
		DocumentStatus status = requiresReview ? DocumentStatus.NEED_REVIEW : DocumentStatus.PROCESSED;
		return new AiDocumentProcessingResponse(documentId, status, requiresReview, List.copyOf(warnings), metadata);
	}

	private AiDocumentResult analyzeSingleImage(String fileName, String contentType, byte[] bytes,
			List<String> documentTypes, List<String> categoryCodes, Company company, OcrPageResult ocr) {
		String prompt = AiDocumentPromptFactory.create(documentTypes, categoryCodes,
				company == null ? null : company.getCompanyName(), company == null ? null : company.getTaxCode(), promptContext(ocr));
		return aiProcessingService.analyzeDocument(new AiDocumentRequest(fileName, contentType, bytes, documentTypes, prompt));
	}

	private List<PageAnalysis> analyzePdfPages(String fileName, byte[] pdfBytes, List<String> documentTypes,
			List<String> categoryCodes, Company company) {
		List<PageAnalysis> results = new ArrayList<>();
		for (PdfPageImage page : pdfImageConversionService.convert(pdfBytes)) {
			OcrPageResult ocr = tess4jOcrService.recognize(page.bytes(), page.pageNumber());
			results.add(new PageAnalysis(analyzeSingleImage(fileName + "#page-" + page.pageNumber(), page.contentType(),
					page.bytes(), documentTypes, categoryCodes, company, ocr), ocr));
		}
		return List.copyOf(results);
	}

	AiDocumentResult analyzeVisionFallback(Document document, byte[] pdfBytes, String prompt,
			DocumentTextExtractionResult extraction, List<String> documentTypes) {
		Set<Integer> fallbackPages = extraction.pages().stream()
				.filter(DocumentTextExtractionResult.PageText::requiresVisionFallback)
				.map(DocumentTextExtractionResult.PageText::pageNumber).collect(Collectors.toUnmodifiableSet());
		List<AiDocumentResult> visionResults = pdfImageConversionService.convert(pdfBytes).stream()
				.filter(page -> fallbackPages.contains(page.pageNumber()))
				.map(page -> aiProcessingService.analyzeDocument(new AiDocumentRequest(
						document.getOriginalFileName() + "#page-" + page.pageNumber(), page.contentType(), page.bytes(),
						documentTypes, prompt)))
				.toList();
		if (visionResults.isEmpty())
			return aiProcessingService.analyzeTextDocument(new AiTextRequest(document.getOriginalFileName(), extraction.text(), prompt));
		if (visionResults.size() == 1) return visionResults.getFirst();
		String combined = extraction.text() + "\n\n[VISION FALLBACK]\n" + visionResults.stream()
				.map(AiDocumentResult::rawText).filter(Objects::nonNull).collect(Collectors.joining("\n\n"));
		return aiProcessingService.analyzeTextDocument(new AiTextRequest(document.getOriginalFileName(), combined, prompt));
	}

	private String promptContext(OcrPageResult page) {
		if (page == null || !page.successful()) return null;
		StringBuilder context = new StringBuilder("page=").append(page.pageNumber()).append("; words:\n");
		page.words().stream().limit(Math.max(1, aiProperties.getOcr().getMaxPromptWords())).forEach(word ->
				context.append(word.id()).append('|').append(word.text().replace('|', ' ')).append('|')
						.append("x=%.5f,y=%.5f,w=%.5f,h=%.5f,c=%.1f%n".formatted(
								word.x(), word.y(), word.width(), word.height(), word.confidence())));
		return context.toString();
	}

	private OcrDocumentResult aggregateOcr(List<OcrPageResult> pages) {
		List<OcrPageResult> successful = pages.stream().filter(OcrPageResult::successful).toList();
		String text = successful.stream().map(OcrPageResult::text).collect(Collectors.joining("\n\n"));
		float confidence = successful.isEmpty() ? 0
				: (float) successful.stream().mapToDouble(OcrPageResult::confidence).average().orElse(0);
		List<String> warnings = pages.stream().map(OcrPageResult::warning).filter(Objects::nonNull).toList();
		return new OcrDocumentResult(text, confidence, List.copyOf(pages),
				pages.stream().mapToLong(OcrPageResult::durationMs).sum(), warnings);
	}

	private AiDocumentResult aggregatePageResults(List<AiDocumentResult> pages) {
		if (pages == null || pages.isEmpty()) throw new BadRequestException("PDF has no analyzable pages");
		AiDocumentResult first = pages.getFirst();
		List<BigDecimal> confidences = pages.stream().map(AiDocumentResult::classificationConfidence).filter(Objects::nonNull).toList();
		BigDecimal confidence = confidences.isEmpty() ? BigDecimal.ZERO : confidences.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(confidences.size()), RoundingMode.HALF_UP);
		String rawText = pages.stream().map(AiDocumentResult::rawText).filter(value -> value != null && !value.isBlank())
				.collect(Collectors.joining("\n\n"));
		List<AiDocumentResult.AiInvoiceItemExtraction> items = pages.stream()
				.filter(page -> page.invoice() != null && page.invoice().items() != null)
				.flatMap(page -> page.invoice().items().stream()).toList();
		AiDocumentResult.AiInvoiceExtraction invoice = first.invoice() == null ? null
				: new AiDocumentResult.AiInvoiceExtraction(first.invoice().invoiceNumber(), first.invoice().invoiceSeries(),
						first.invoice().invoiceDate(), first.invoice().sellerName(), first.invoice().sellerTaxCode(),
						first.invoice().sellerAddress(), first.invoice().buyerName(), first.invoice().buyerTaxCode(),
						first.invoice().buyerAddress(), first.invoice().subtotal(), first.invoice().vatAmount(),
						first.invoice().totalAmount(), items);
		return new AiDocumentResult(first.provider(), first.model(), first.documentType(), confidence,
				first.accountingCategoryCode(), first.accountingAccount(), rawText, invoice,
				pages.stream().flatMap(page -> page.fields() == null ? java.util.stream.Stream.empty() : page.fields().stream()).toList(),
				pages.stream().flatMap(page -> page.warnings() == null ? java.util.stream.Stream.empty() : page.warnings().stream()).toList(),
				first.rawResponse(), pages.stream().mapToLong(AiDocumentResult::durationMs).sum(),
				first.documentDirection(), first.transactionAssessment(),
				pages.stream().flatMap(page -> page.extraFields() == null ? java.util.stream.Stream.empty() : page.extraFields().stream()).toList(),
				first.companyRole());
	}

	private AiDocumentResult resolveCompanyRole(AiDocumentResult result, Company company) {
		String companyTaxCode = normalizeTaxCode(company == null ? null : company.getTaxCode());
		AiDocumentResult.AiInvoiceExtraction invoice = result.invoice();
		if (companyTaxCode == null || invoice == null) return result;
		boolean seller = companyTaxCode.equals(normalizeTaxCode(invoice.sellerTaxCode()));
		boolean buyer = companyTaxCode.equals(normalizeTaxCode(invoice.buyerTaxCode()));
		AiDocumentResult.AiCompanyRole role = null;
		if (seller && buyer) role = new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.UNKNOWN, BigDecimal.ONE,
				"Current company tax code matches both seller and buyer");
		else if (seller) role = new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.SELLER, BigDecimal.ONE,
				"Current company tax code exactly matches seller tax code");
		else if (buyer) role = new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.BUYER, BigDecimal.ONE,
				"Current company tax code exactly matches buyer tax code");
		return role == null ? result : result.withCompanyRoleAndDirection(role, result.documentDirection());
	}

	private void logOcr(Long documentId, OcrPageResult ocr) {
		processingLogService.append(documentId, "TESSERACT_OCR", "RECOGNIZE", ocr.successful() ? "SUCCESS" : "WARNING",
				BigDecimal.valueOf(ocr.confidence()).movePointLeft(2), ocr.durationMs(),
				ocr.successful() ? "page=%d; words=%d".formatted(ocr.pageNumber(), ocr.words().size()) : ocr.warning());
	}

	private boolean isSupportedImage(String contentType) {
		return "image/jpeg".equalsIgnoreCase(contentType) || "image/png".equalsIgnoreCase(contentType);
	}

	private boolean isPdf(String contentType) { return "application/pdf".equalsIgnoreCase(contentType); }

	private String normalizeTaxCode(String value) {
		if (value == null || value.isBlank()) return null;
		String normalized = value.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
		return normalized.isBlank() ? null : normalized;
	}

	private String errorCode(RuntimeException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage().toUpperCase(Locale.ROOT);
		for (String code : List.of("PDF_EXTRACTION_FAILED", "OCR_LOW_CONFIDENCE", "OCR_FAILED", "AI_TIMEOUT",
				"AI_INVALID_RESPONSE", "AI_EXTRACTION_FAILED", "OLLAMA_CONNECT_TIMEOUT", "OLLAMA_READ_TIMEOUT",
				"OLLAMA_CALL_TIMEOUT", "OLLAMA_UNREACHABLE", "OLLAMA_INVALID_RESPONSE", "OLLAMA_EMPTY_RESPONSE",
				"OLLAMA_MODEL_NOT_FOUND", "OLLAMA_TOKEN_LIMIT", "VALIDATION_FAILED", "PERSISTENCE_FAILED"))
			if (message.contains(code)) return code;
		return message.contains("OLLAMA") || message.contains("AI_") ? "AI_PROVIDER_ERROR" : "PERSISTENCE_FAILED";
	}

	private String sanitize(String message) {
		String value = message == null || message.isBlank() ? "Document processing failed" : message;
		return value.substring(0, Math.min(500, value.length()));
	}

	private record PageAnalysis(AiDocumentResult aiResult, OcrPageResult ocr) {}
}
