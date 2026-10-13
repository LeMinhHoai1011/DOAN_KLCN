package com.example.invoice.service;

import com.example.invoice.ai.AiDocumentPromptFactory;
import com.example.invoice.ai.AiDocumentPromptFactory.AccountingCategoryOption;
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
import com.example.invoice.entity.AccountingCategory;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.AiProviderException;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import com.example.invoice.dto.document.OCRResultResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
	private final AdaptiveOcrService adaptiveOcrService;
	private final PdfImageConversionService pdfImageConversionService;
	private final AiProperties aiProperties;
	private final OcrTextNormalizer ocrTextNormalizer;
	private final OcrLayoutReconstructor ocrLayoutReconstructor;
	private final OCRResultService ocrResultService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Autowired
	public DocumentAiProcessingService(DocumentService documentService, DocumentTypeRepository documentTypeRepository,
			AccountingCategoryRepository accountingCategoryRepository, AiProcessingService aiProcessingService,
			AiDocumentResultValidator resultValidator, DocumentAiResultPersistenceService persistenceService,
			ProcessingLogService processingLogService, PdfImageConversionService pdfImageConversionService,
			AdaptiveOcrService adaptiveOcrService,
			AiProperties aiProperties, OcrTextNormalizer ocrTextNormalizer,
			OcrLayoutReconstructor ocrLayoutReconstructor, OCRResultService ocrResultService) {
		this.documentService = documentService;
		this.documentTypeRepository = documentTypeRepository;
		this.accountingCategoryRepository = accountingCategoryRepository;
		this.aiProcessingService = aiProcessingService;
		this.resultValidator = resultValidator;
		this.persistenceService = persistenceService;
		this.processingLogService = processingLogService;
		this.adaptiveOcrService = adaptiveOcrService;
		this.pdfImageConversionService = pdfImageConversionService;
		this.aiProperties = aiProperties;
		this.ocrTextNormalizer = ocrTextNormalizer;
		this.ocrLayoutReconstructor = ocrLayoutReconstructor;
		this.ocrResultService = ocrResultService;
	}

	/** Compatibility constructor retained for the text-first PDF fallback tests. */
	DocumentAiProcessingService(DocumentService documentService, DocumentTypeRepository documentTypeRepository,
			AccountingCategoryRepository accountingCategoryRepository, DocumentTextExtractionService ignoredExtractionService,
			OCRResultService ignoredOcrResultService, AiProcessingService aiProcessingService,
			AiDocumentResultValidator resultValidator, DocumentAiResultPersistenceService persistenceService,
			ProcessingLogService processingLogService, PdfImageConversionService pdfImageConversionService) {
		this(documentService, documentTypeRepository, accountingCategoryRepository, aiProcessingService, resultValidator,
				persistenceService, processingLogService, pdfImageConversionService, null, new AiProperties(), null, null, null);
	}

	public AiDocumentProcessingResponse process(Long documentId, boolean reprocess) {
		Document document = documentService.load(documentId);
		if (!isSupportedImage(document.getFileType()) && !isPdf(document.getFileType()))
			throw new BadRequestException("Chỉ hỗ trợ tệp PDF, JPEG và PNG");
		document = documentService.claimForAiProcessing(documentId, reprocess);

		try {
		List<DocumentType> types = documentTypeRepository.findByActiveTrue();
		if (types.isEmpty()) throw new BadRequestException("Chưa cấu hình loại chứng từ đang hoạt động để AI phân loại");
		List<String> typeCodes = types.stream().map(DocumentType::getCode).toList();
		Set<String> allowedTypes = Set.copyOf(typeCodes);
		Company company = document.getCompany();
		if (company == null) throw new BadRequestException("CATEGORY_COMPANY_MISSING: chứng từ chưa thuộc doanh nghiệp");
		long categoryLoadStarted = System.nanoTime();
		log.info("CATEGORY_LOAD_STARTED documentId={} companyId={}", documentId, company.getId());
		List<AccountingCategory> categories = accountingCategoryRepository
				.findByCompanyIdAndActiveTrueOrderByCategoryCodeAsc(company.getId());
		log.info("CATEGORY_LOAD_COMPLETED documentId={} companyId={} categoryCount={} durationMs={}", documentId,
				company.getId(), categories.size(), Duration.ofNanos(System.nanoTime() - categoryLoadStarted).toMillis());
		List<String> categoryCodes = categories.stream().map(AccountingCategory::getCategoryCode).toList();
		List<AccountingCategoryOption> categoryOptions = categories.stream()
				.map(category -> new AccountingCategoryOption(category.getCategoryCode(), category.getCategoryName(), category.getDescription()))
				.toList();

		byte[] bytes = documentService.loadFileBytes(documentId);
		if (bytes.length == 0) throw new BadRequestException("Nội dung chứng từ tải từ MinIO bị trống");
		if (document.getFileSize() != null && document.getFileSize() != bytes.length)
			throw new BadRequestException("Kích thước nội dung chứng từ không khớp với siêu dữ liệu đã lưu");

		processingLogService.append(documentId, "AI_DOCUMENT", "START", "PROCESSING", null, null,
				reprocess ? "AI reprocess requested" : "AI process requested");

			return isPdf(document.getFileType())
					? processPdf(documentId, document, bytes, typeCodes, categoryCodes, categoryOptions, allowedTypes, company)
					: processImage(documentId, document, bytes, typeCodes, categoryCodes, categoryOptions, allowedTypes, company, reprocess);
		} catch (RuntimeException exception) {
			try {
				documentService.updateProcessingStatus(documentId, DocumentStatus.FAILED);
			} catch (RuntimeException statusException) {
				log.error("Could not mark failed documentId={}", documentId, statusException);
			}
			try {
				processingLogService.append(documentId, "DOCUMENT_PIPELINE", errorCode(exception), "FAILED",
						BigDecimal.ZERO, null, sanitize(exception.getMessage()));
			} catch (RuntimeException logException) {
				log.error("Could not persist failure log documentId={} originalError={}", documentId,
						exception.getMessage(), logException);
			}
			throw exception;
		}
	}

	private AiDocumentProcessingResponse processPdf(Long documentId, Document document, byte[] bytes,
			List<String> typeCodes, List<String> categoryCodes, List<AccountingCategoryOption> categoryOptions,
			Set<String> allowedTypes, Company company) {
		List<PageAnalysis> analyzedPages = analyzePdfPages(documentId, document.getOriginalFileName(), bytes,
				typeCodes, categoryCodes, categoryOptions, company);
		OcrDocumentResult ocr = aggregateOcr(analyzedPages.stream().map(PageAnalysis::ocr).toList());

		AiDocumentResult result = resolveCompanyRole(aggregatePageResults(
				analyzedPages.stream().map(PageAnalysis::aiResult).toList()), company);
		logItemTelemetry(documentId, result);
		result = addMissingInvoiceItemsWarning(result, ocr);

		ValidatedAiDocumentResult validated = resultValidator.validate(result, allowedTypes, Set.copyOf(categoryCodes));
		processingLogService.append(documentId, "AI_DOCUMENT", "ANALYZE", "SUCCESS", validated.confidence(),
				result.durationMs(), "AI response parsed and validated", result.provider(), result.model());
		boolean requiresReview = persistenceService.persist(documentId, validated, ocr);
		log.info("AI_CLASSIFICATION_COMPLETED documentId={} companyId={} model={} categoryCode={} needsReview={} durationMs={}",
				documentId, company.getId(), result.model(), result.accountingCategoryCode(), requiresReview, result.durationMs());
		List<String> warnings = new ArrayList<>(validated.warnings());
		if (ocr != null) warnings.addAll(ocr.warnings());
		return response(documentId, requiresReview, warnings, null);
	}

	private AiDocumentProcessingResponse processImage(Long documentId, Document document, byte[] originalBytes,
			List<String> typeCodes, List<String> categoryCodes, List<AccountingCategoryOption> categoryOptions,
			Set<String> allowedTypes, Company company, boolean reprocess) {
		List<String> warnings = new ArrayList<>();
		OcrPageResult pageOcr = null;
		byte[] bytes = originalBytes;
		String contentType = document.getFileType();
		boolean skipOcr = false;
		if (reprocess && ocrResultService != null) {
			try {
				OCRResultResponse existingOcr = ocrResultService.findByDocumentId(documentId);
				Optional<OcrPageResult> restored = restoreStoredOcr(existingOcr);
				if (restored.isPresent()) {
					pageOcr = restored.get();
					skipOcr = true;
				} else if (existingOcr != null && existingOcr.rawText() != null && !existingOcr.rawText().isBlank()) {
					warnings.add("Stored OCR layout is unavailable or invalid; OCR was run again before AI analysis");
					log.info("Stored OCR is not safe for text-only reuse documentId={}; falling back to adaptive OCR", documentId);
				}
			} catch (com.example.invoice.exception.ResourceNotFoundException exception) {
				log.debug("No prior OCR result is available for reprocess documentId={}", documentId);
			} catch (RuntimeException exception) {
				log.warn("Could not load prior OCR result for reprocess documentId={}: {}", documentId,
						sanitize(exception.getMessage()));
			}
		}

		OcrDocumentResult ocr = null;
		ImagePreprocessingMetadata metadata = null;

		if (!skipOcr) {
			AdaptiveOcrService.AdaptiveOcrResult adaptive = adaptiveOcrService.recognize(originalBytes, document.getFileType(), 1);
			bytes = adaptive.selectedImageBytes();
			contentType = adaptive.selectedContentType();
			if (adaptive.warning() != null) warnings.add(adaptive.warning());
			String objectKey = null;
			if (adaptive.selectedProfile() != OcrProcessingProfile.NORMAL) {
				try {
					objectKey = documentService.storeProcessedImage(documentId, bytes, contentType);
				} catch (RuntimeException exception) {
					warnings.add("Không thể lưu bản ảnh đã tiền xử lý; kết quả OCR vẫn được giữ lại");
					log.warn("Could not store processed image documentId={}: {}", documentId, sanitize(exception.getMessage()));
				}
			}
			processingLogService.append(documentId, "IMAGE_PREPROCESSING", adaptive.selectedProfile().name(), "SUCCESS",
					BigDecimal.valueOf(adaptive.qualityScore()), null,
					"profile=%s; attempts=%d; original=%s; selectedObjectKey=%s".formatted(adaptive.selectedProfile(),
							adaptive.attempts(), adaptive.imageQuality() == null ? "unknown" : adaptive.imageQuality().width() + "x" + adaptive.imageQuality().height(), objectKey));

			log.info("AI input documentId={} mimeType={} bytes={} allowedTypeCount={}",
					documentId, contentType, bytes.length, typeCodes.size());
			pageOcr = reconstructLayout(adaptive.page());
			logOcr(documentId, pageOcr);
			ocr = aggregateOcr(List.of(pageOcr));
			persistenceService.persistOcr(documentId, ocr);
			metadata = adaptive.imageQuality() == null ? null : new ImagePreprocessingMetadata(
					adaptive.selectedProfile() != OcrProcessingProfile.NORMAL, adaptive.imageQuality().skewAngleDegrees(),
					adaptive.imageQuality().width(), adaptive.imageQuality().height(), pageOcr.imageWidth(), pageOcr.imageHeight(),
					pageOcr.durationMs(), adaptive.warning());
		}

		AiDocumentResult result = resolveCompanyRole(analyzeSingleImage(document.getOriginalFileName(), contentType,
				bytes, typeCodes, categoryCodes, categoryOptions, company, pageOcr), company);
		logItemTelemetry(documentId, result);
		result = addMissingInvoiceItemsWarning(result, ocr);
		ValidatedAiDocumentResult validated = resultValidator.validate(result, allowedTypes, Set.copyOf(categoryCodes));
		processingLogService.append(documentId, "AI_DOCUMENT", "ANALYZE", "SUCCESS", validated.confidence(),
				result.durationMs(), "AI response parsed and validated", result.provider(), result.model());
		boolean requiresReview = persistenceService.persist(documentId, validated, ocr);
		log.info("AI_CLASSIFICATION_COMPLETED documentId={} companyId={} model={} categoryCode={} needsReview={} durationMs={}",
				documentId, company.getId(), result.model(), result.accountingCategoryCode(), requiresReview, result.durationMs());
		warnings.addAll(validated.warnings());
		if (ocr != null) warnings.addAll(ocr.warnings());

		return response(documentId, requiresReview, warnings, metadata);
	}

	private AiDocumentProcessingResponse response(Long documentId, boolean requiresReview, List<String> warnings,
			ImagePreprocessingMetadata metadata) {
		DocumentStatus status = requiresReview ? DocumentStatus.NEED_REVIEW : DocumentStatus.PROCESSED;
		return new AiDocumentProcessingResponse(documentId, status, requiresReview, List.copyOf(warnings), metadata);
	}

	AiDocumentResult analyzeSingleImage(String fileName, String contentType, byte[] bytes,
			List<String> documentTypes, List<String> categoryCodes, Company company, OcrPageResult ocr) {
		List<AccountingCategoryOption> categoryOptions = categoryCodes.stream()
				.map(code -> new AccountingCategoryOption(code, null, null)).toList();
		return analyzeSingleImage(fileName, contentType, bytes, documentTypes, categoryCodes, categoryOptions, company, ocr);
	}

	AiDocumentResult analyzeSingleImage(String fileName, String contentType, byte[] bytes,
			List<String> documentTypes, List<String> categoryCodes, List<AccountingCategoryOption> categoryOptions,
			Company company, OcrPageResult ocr) {
		String normalizedText = normalizeOcrText(ocr == null ? null
				: ocrLayoutReconstructor == null ? ocr.text() : ocrLayoutReconstructor.structuredContext(ocr));
		String prompt = AiDocumentPromptFactory.createWithCategories(documentTypes, categoryOptions,
				company == null ? null : company.getCompanyName(), company == null ? null : company.getTaxCode(), null);
		boolean reliableOcr = ocr != null && ocr.successful()
				&& Float.isFinite(ocr.confidence())
				&& !ocr.words().isEmpty() && !ocr.lines().isEmpty() && !ocr.blocks().isEmpty()
				&& !looksLikeLostVietnameseDiacritics(normalizedText, ocr.language())
				&& BigDecimal.valueOf(ocr.confidence()).movePointLeft(2).compareTo(aiProperties.getOcr().getGoodConfidence()) >= 0;
		if (reliableOcr && !normalizedText.isBlank()) {
			log.info("AI text input file={} ocrChars={} ocrWords={} confidence={} context={} reservedOutput={}",
					fileName, normalizedText.length(), ocr.words().size(), ocr.confidence(),
					aiProperties.getOllama().getNumContext(), aiProperties.getOcr().getReservedOutputTokens());
			String textPrompt = AiDocumentPromptFactory.forTextExtraction(prompt);
			try {
				log.info("AI structured extraction file={} requestMode=TEXT retryAttempt=0", fileName);
				return aiProcessingService.analyzeTextDocument(new AiTextRequest(fileName, normalizedText, textPrompt));
			} catch (AiProviderException firstFailure) {
				if (!isStructuredOutputFailure(firstFailure)) throw firstFailure;
				try {
					log.warn("AI structured extraction retry file={} requestMode=TEXT retryAttempt=1 code={}",
							fileName, errorCode(firstFailure));
					return aiProcessingService.analyzeTextDocument(new AiTextRequest(fileName, normalizedText,
							AiDocumentPromptFactory.strictRetry(textPrompt)));
				} catch (AiProviderException retryFailure) {
					if (!isStructuredOutputFailure(retryFailure)) throw retryFailure;
					log.warn("AI text extraction exhausted; falling back to vision file={} requestMode=VISION retryAttempt=0 code={}",
							fileName, errorCode(retryFailure));
					return aiProcessingService.analyzeDocument(new AiDocumentRequest(
							fileName, contentType, bytes, documentTypes, prompt));
				}
			}
		}
		log.info("AI vision fallback file={} reason={} imageBytes={}", fileName,
				ocr == null ? "OCR_UNAVAILABLE" : "OCR_LOW_CONFIDENCE", bytes.length);
		return aiProcessingService.analyzeDocument(new AiDocumentRequest(fileName, contentType, bytes, documentTypes, prompt));
	}

	Optional<OcrPageResult> restoreStoredOcr(OCRResultResponse stored) {
		if (stored == null || stored.rawText() == null || stored.rawText().isBlank()
				|| stored.layoutJson() == null || stored.layoutJson().isBlank()) return Optional.empty();
		try {
			JsonNode root = objectMapper.readTree(stored.layoutJson());
			JsonNode pages = root.path("pages");
			if (!pages.isArray() || pages.size() != 1) return Optional.empty();
			JsonNode page = pages.get(0);
			int width = page.path("width").asInt(0);
			int height = page.path("height").asInt(0);
			if (width <= 0 || height <= 0) return Optional.empty();
			List<OcrWord> words = readWords(page.path("words"), width, height);
			if (words.isEmpty()) return Optional.empty();
			int pageNumber = page.path("page").asInt(1);
			float confidence = stored.confidence() == null
					? Float.NaN : stored.confidence().movePointRight(2).floatValue();
			String language = textOrFallback(page.path("language"), stored.language());
			List<OcrLine> lines = readLines(page.path("lines"), pageNumber, width, height);
			List<OcrBlock> blocks = readBlocks(page.path("blocks"), pageNumber, width, height);
			OcrPageResult restored = new OcrPageResult(pageNumber, width, height, stored.rawText(), confidence,
					words, 0, null, language, lines, blocks);
			if (lines.isEmpty() || blocks.isEmpty()) restored = reconstructLayout(restored);
			return restored.lines().isEmpty() || restored.blocks().isEmpty() ? Optional.empty() : Optional.of(restored);
		} catch (RuntimeException | java.io.IOException exception) {
			log.warn("Could not restore stored OCR layout; the original OCR row is left unchanged: {}",
					sanitize(exception.getMessage()));
			return Optional.empty();
		}
	}

	private List<OcrWord> readWords(JsonNode values, int pageWidth, int pageHeight) {
		if (!values.isArray()) return List.of();
		List<OcrWord> result = new ArrayList<>();
		for (JsonNode value : values) {
			String id = value.path("id").asText("");
			String text = value.path("text").asText("");
			if (id.isBlank() || text.isBlank()) continue;
			result.add(new OcrWord(id, text, (float) value.path("confidence").asDouble(0),
					normalized(value, "x", pageWidth), normalized(value, "y", pageHeight),
					normalized(value, "width", pageWidth), normalized(value, "height", pageHeight)));
		}
		return List.copyOf(result);
	}

	private List<OcrLine> readLines(JsonNode values, int pageNumber, int pageWidth, int pageHeight) {
		if (!values.isArray()) return List.of();
		List<OcrLine> result = new ArrayList<>();
		for (JsonNode value : values) {
			String id = value.path("id").asText("");
			if (id.isBlank()) continue;
			result.add(new OcrLine(id, pageNumber, value.path("text").asText(""),
					(float) value.path("confidence").asDouble(0), normalized(value, "x", pageWidth),
					normalized(value, "y", pageHeight), normalized(value, "width", pageWidth),
					normalized(value, "height", pageHeight), readIds(value.path("wordIds"))));
		}
		return List.copyOf(result);
	}

	private List<OcrBlock> readBlocks(JsonNode values, int pageNumber, int pageWidth, int pageHeight) {
		if (!values.isArray()) return List.of();
		List<OcrBlock> result = new ArrayList<>();
		for (JsonNode value : values) {
			String id = value.path("id").asText("");
			if (id.isBlank()) continue;
			result.add(new OcrBlock(id, pageNumber, value.path("text").asText(""),
					(float) value.path("confidence").asDouble(0), normalized(value, "x", pageWidth),
					normalized(value, "y", pageHeight), normalized(value, "width", pageWidth),
					normalized(value, "height", pageHeight), readIds(value.path("lineIds"))));
		}
		return List.copyOf(result);
	}

	private List<String> readIds(JsonNode values) {
		if (!values.isArray()) return List.of();
		List<String> result = new ArrayList<>();
		values.forEach(value -> { if (!value.asText("").isBlank()) result.add(value.asText()); });
		return List.copyOf(result);
	}

	private double normalized(JsonNode value, String field, int dimension) {
		double pixels = value.path(field).asDouble(Double.NaN);
		if (!Double.isFinite(pixels) || pixels < 0 || dimension <= 0) return 0;
		return Math.min(1d, pixels / dimension);
	}

	private String textOrFallback(JsonNode value, String fallback) {
		String text = value.asText("");
		return text.isBlank() ? fallback : text;
	}

	private boolean looksLikeLostVietnameseDiacritics(String text, String language) {
		if (text == null || language == null || !language.toLowerCase(Locale.ROOT).contains("vie")) return false;
		String lower = text.toLowerCase(Locale.ROOT);
		String[] suspiciousPhrases = {
				"hoa don", "cong ty", "dia chi", "don vi", "noi that", "thanh pho", "ha noi",
				"ma so thue", "nguoi mua", "nguoi ban", "thanh tien", "tong cong", "tien thue",
				"so tien", "lam trieu"
		};
		long suspicious = java.util.Arrays.stream(suspiciousPhrases)
				.filter(phrase -> lower.matches("(?s).*\\b" + java.util.regex.Pattern.quote(phrase) + "\\b.*"))
				.count();
		boolean hasVietnameseDiacritics = text.matches("(?s).*[À-ưǍ-ǜẠ-ỹĐđ].*");
		// A single ASCII phrase can be a legitimate brand or bilingual label. Three or
		// more known Vietnamese phrases without their marks, even inside otherwise
		// accented OCR, is strong evidence that Tesseract confidence is over-optimistic.
		return hasVietnameseDiacritics ? suspicious >= 3 : suspicious >= 1;
	}

	private boolean isStructuredOutputFailure(AiProviderException exception) {
		String message = exception.getMessage() == null ? "" : exception.getMessage().toUpperCase(Locale.ROOT);
		return message.contains("OLLAMA_EMPTY_RESPONSE") || message.contains("OLLAMA_INVALID_JSON")
				|| message.contains("OLLAMA_TOKEN_LIMIT") || message.contains("AI_JSON_PARSE_ERROR");
	}

	private List<PageAnalysis> analyzePdfPages(Long documentId, String fileName, byte[] pdfBytes, List<String> documentTypes,
			List<String> categoryCodes, List<AccountingCategoryOption> categoryOptions, Company company) {
		List<PageAnalysis> results = new ArrayList<>();
		List<OcrPageResult> recognizedPages = new ArrayList<>();
		for (PdfPageImage page : pdfImageConversionService.convert(pdfBytes)) {
			AdaptiveOcrService.AdaptiveOcrResult adaptive = adaptiveOcrService.recognize(page.bytes(), page.contentType(), page.pageNumber());
			OcrPageResult ocr = reconstructLayout(adaptive.page());
			logOcr(documentId, ocr);
			recognizedPages.add(ocr);
			persistenceService.persistOcr(documentId, aggregateOcr(recognizedPages));
			results.add(new PageAnalysis(analyzeSingleImage(fileName + "#page-" + page.pageNumber(), adaptive.selectedContentType(),
					adaptive.selectedImageBytes(), documentTypes, categoryCodes, categoryOptions, company, ocr), ocr));
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

	String normalizeOcrText(String value) {
		if (ocrTextNormalizer != null) return ocrTextNormalizer.normalizeAndCompact(value);
		return value == null ? "" : value.replaceAll("[ \\t]+", " ").replaceAll("(?m)^\\s*$\\R", "").trim();
	}

	private OcrPageResult reconstructLayout(OcrPageResult page) {
		return ocrLayoutReconstructor == null ? page : ocrLayoutReconstructor.reconstruct(page);
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
		if (pages == null || pages.isEmpty()) throw new BadRequestException("Tệp PDF không có trang nào có thể phân tích");
		AiDocumentResult first = pages.getFirst();
		List<BigDecimal> confidences = pages.stream().map(AiDocumentResult::classificationConfidence).filter(Objects::nonNull).toList();
		BigDecimal confidence = confidences.isEmpty() ? BigDecimal.ZERO : confidences.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
				.divide(BigDecimal.valueOf(confidences.size()), RoundingMode.HALF_UP);
		String rawText = pages.stream().map(AiDocumentResult::rawText).filter(value -> value != null && !value.isBlank())
				.collect(Collectors.joining("\n\n"));
		List<AiDocumentResult.AiInvoiceItemExtraction> items = pages.stream()
				.filter(page -> page.invoice() != null && page.invoice().items() != null)
				.flatMap(page -> page.invoice().items().stream()).distinct().toList();
		List<String> warnings = pages.stream()
				.flatMap(page -> page.warnings() == null ? java.util.stream.Stream.empty() : page.warnings().stream())
				.collect(Collectors.toCollection(ArrayList::new));
		if (pages.stream().map(AiDocumentResult::documentType).filter(Objects::nonNull).distinct().count() > 1) {
			warnings.add("DOCUMENT_TYPE_PAGE_CONFLICT: Các trang PDF đề xuất loại chứng từ khác nhau; cần kiểm tra thủ công");
		}
		AiDocumentResult.AiInvoiceExtraction invoice = pages.stream().map(AiDocumentResult::invoice)
				.filter(Objects::nonNull).reduce((left, right) -> mergeInvoice(left, right, items)).orElse(null);
		return new AiDocumentResult(first.provider(), first.model(), first.documentType(), confidence,
				first.accountingCategoryCode(), first.accountingAccount(), rawText, invoice,
				pages.stream().flatMap(page -> page.fields() == null ? java.util.stream.Stream.empty() : page.fields().stream()).toList(),
				List.copyOf(warnings),
				first.rawResponse(), pages.stream().mapToLong(AiDocumentResult::durationMs).sum(),
				first.documentDirection(), first.transactionAssessment(),
				pages.stream().flatMap(page -> page.extraFields() == null ? java.util.stream.Stream.empty() : page.extraFields().stream()).toList(),
				first.companyRole());
	}

	AiDocumentResult addMissingInvoiceItemsWarning(AiDocumentResult result, OcrDocumentResult ocr) {
		if (result == null || !"VAT_INVOICE".equals(normalizeType(result.documentType())) || !hasItemTable(ocr)) return result;
		List<AiDocumentResult.AiInvoiceItemExtraction> items = result.invoice() == null ? null : result.invoice().items();
		boolean hasValidItem = items != null && items.stream().anyMatch(item -> item != null
				&& item.productName() != null && !item.productName().isBlank());
		if (hasValidItem) return result;
		List<String> warnings = new ArrayList<>(result.warnings() == null ? List.of() : result.warnings());
		if (warnings.stream().noneMatch(warning -> warning != null && warning.startsWith("INVOICE_ITEMS_MISSING"))) {
			warnings.add("INVOICE_ITEMS_MISSING: OCR indicates an item table but AI returned no valid invoice items");
		}
		return new AiDocumentResult(result.provider(), result.model(), result.documentType(), result.classificationConfidence(),
				result.accountingCategoryCode(), result.accountingAccount(), result.rawText(), result.invoice(), result.fields(),
				List.copyOf(warnings), result.rawResponse(), result.durationMs(), result.documentDirection(),
				result.transactionAssessment(), result.extraFields(), result.companyRole());
	}

	private void logItemTelemetry(Long documentId, AiDocumentResult result) {
		List<AiDocumentResult.AiInvoiceItemExtraction> items = result == null || result.invoice() == null
				? null : result.invoice().items();
		long invalidItemCount = items == null ? 0 : items.stream()
				.filter(item -> item == null || item.productName() == null || item.productName().isBlank()).count();
		log.info("AI_ITEMS_AFTER_PARSER documentId={} invoicePresent={} itemsPresent={} itemCount={} invalidItemCount={}",
				documentId, result != null && result.invoice() != null, items != null,
				items == null ? 0 : items.size(), invalidItemCount);
	}

	private boolean hasItemTable(OcrDocumentResult ocr) {
		if (ocr == null || ocr.rawText() == null || ocr.rawText().isBlank()) return false;
		String text = Normalizer.normalize(ocr.rawText(), Normalizer.Form.NFD)
				.replaceAll("\\p{M}+", "").toLowerCase(java.util.Locale.ROOT);
		boolean description = text.contains("ten hang hoa") || text.contains("hang hoa, dich vu") || text.contains("description");
		boolean quantity = text.contains("so luong") || text.contains("quantity");
		boolean unitPrice = text.contains("don gia") || text.contains("unit price");
		boolean amount = text.contains("thanh tien") || text.contains("amount");
		return description && quantity && unitPrice && amount;
	}

	private String normalizeType(String type) {
		return type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
	}

	private AiDocumentResult.AiInvoiceExtraction mergeInvoice(AiDocumentResult.AiInvoiceExtraction left,
			AiDocumentResult.AiInvoiceExtraction right, List<AiDocumentResult.AiInvoiceItemExtraction> items) {
		return new AiDocumentResult.AiInvoiceExtraction(firstPresent(left.invoiceNumber(), right.invoiceNumber()),
				firstPresent(left.invoiceSeries(), right.invoiceSeries()), firstPresent(left.invoiceDate(), right.invoiceDate()),
				firstPresent(left.sellerName(), right.sellerName()), firstPresent(left.sellerTaxCode(), right.sellerTaxCode()),
				firstPresent(left.sellerAddress(), right.sellerAddress()), firstPresent(left.sellerPhone(), right.sellerPhone()),
				firstPresent(left.buyerName(), right.buyerName()),
				firstPresent(left.buyerTaxCode(), right.buyerTaxCode()), firstPresent(left.buyerAddress(), right.buyerAddress()),
				left.subtotal() != null ? left.subtotal() : right.subtotal(),
				left.vatAmount() != null ? left.vatAmount() : right.vatAmount(),
				left.totalAmount() != null ? left.totalAmount() : right.totalAmount(),
				firstPresent(left.paymentMethod(), right.paymentMethod()), firstPresent(left.amountInWords(), right.amountInWords()),
				firstPresent(left.taxAuthorityCode(), right.taxAuthorityCode()), firstPresent(left.signDate(), right.signDate()), items);
	}

	private String firstPresent(String left, String right) {
		return left != null && !left.isBlank() ? left : right;
	}

	private AiDocumentResult resolveCompanyRole(AiDocumentResult result, Company company) {
		String companyTaxCode = normalizeTaxCode(company == null ? null : company.getTaxCode());
		AiDocumentResult.AiInvoiceExtraction invoice = result.invoice();
		if (companyTaxCode == null || invoice == null) return result;
		boolean seller = companyTaxCode.equals(normalizeTaxCode(invoice.sellerTaxCode()));
		boolean buyer = companyTaxCode.equals(normalizeTaxCode(invoice.buyerTaxCode()));
		AiDocumentResult.AiCompanyRole role = null;
		if (seller && buyer) role = new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.UNKNOWN, BigDecimal.ONE,
				"Mã số thuế của công ty hiện tại trùng với cả người bán và người mua");
		else if (seller) role = new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.SELLER, BigDecimal.ONE,
				"Mã số thuế của công ty hiện tại trùng chính xác với mã số thuế người bán");
		else if (buyer) role = new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.BUYER, BigDecimal.ONE,
				"Mã số thuế của công ty hiện tại trùng chính xác với mã số thuế người mua");
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
		for (String code : List.of("PDF_EXTRACTION_FAILED", "OCR_LOW_CONFIDENCE", "OCR_FAILED", "AI_CONTEXT_EXCEEDED",
				"AI_CONNECTION_ERROR", "AI_TIMEOUT", "AI_HTTP_ERROR", "AI_INVALID_RESPONSE", "AI_JSON_PARSE_ERROR",
				"AI_EXTRACTION_FAILED", "OLLAMA_CONNECT_TIMEOUT", "OLLAMA_READ_TIMEOUT",
				"OLLAMA_CALL_TIMEOUT", "OLLAMA_UNREACHABLE", "OLLAMA_INVALID_RESPONSE", "OLLAMA_EMPTY_RESPONSE",
				"OLLAMA_MODEL_NOT_FOUND", "OLLAMA_INVALID_JSON", "OLLAMA_TOKEN_LIMIT", "VALIDATION_FAILED", "PERSISTENCE_FAILED"))
			if (message.contains(code)) return code;
		return message.contains("OLLAMA") || message.contains("AI_") ? "AI_PROVIDER_ERROR" : "PERSISTENCE_FAILED";
	}

	private String sanitize(String message) {
		String value = message == null || message.isBlank() ? "Xử lý chứng từ thất bại" : message;
		return value.substring(0, Math.min(255, value.length()));
	}

	private record PageAnalysis(AiDocumentResult aiResult, OcrPageResult ocr) {}
}
