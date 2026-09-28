package com.example.invoice.service;

import com.example.invoice.ai.AiDocumentPromptFactory;
import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.ai.AiDocumentResultValidator.ValidatedAiDocumentResult;
import com.example.invoice.ai.AiProcessingService;
import com.example.invoice.dto.ai.AiDocumentProcessingResponse;
import com.example.invoice.dto.ai.AiDocumentRequest;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.dto.ai.ImagePreprocessingMetadata;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.entity.Company;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.DocumentTypeRepository;
import com.example.invoice.repository.AccountingCategoryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;
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
	private final ImagePreprocessingService imagePreprocessingService;
	private final PdfImageConversionService pdfImageConversionService;
	private final AccountingCategoryRepository accountingCategoryRepository;

	public AiDocumentProcessingResponse process(Long documentId, boolean reprocess) {
		Document document = documentService.load(documentId);
		if (document.getStatus() == DocumentStatus.PROCESSING) {
			throw new BadRequestException("Document is already being processed");
		}
		if (!reprocess && document.getStatus() != DocumentStatus.UPLOADED) {
			throw new BadRequestException("Document has already been processed; use reprocess to run AI again");
		}
		if (!isSupportedImage(document.getFileType()) && !"application/pdf".equalsIgnoreCase(document.getFileType())) {
			throw new BadRequestException("Only image/jpeg and image/png are supported for AI document processing");
		}

		List<DocumentType> types = documentTypeRepository.findByActiveTrue();
		if (types.isEmpty()) {
			throw new BadRequestException("No active document types are configured for AI classification");
		}
		List<String> typeCodes = types.stream().map(DocumentType::getCode).toList();
		Company company = document.getCompany();
		List<String> categoryCodes = company == null ? List.of() : accountingCategoryRepository.findByCompanyId(company.getId()).stream().filter(com.example.invoice.entity.AccountingCategory::isActive).map(com.example.invoice.entity.AccountingCategory::getCategoryCode).toList();
		Set<String> allowedTypes = typeCodes.stream().collect(Collectors.toUnmodifiableSet());
		byte[] bytes = documentService.loadFileBytes(documentId);
		if (bytes.length == 0) {
			throw new BadRequestException("AI document content loaded from MinIO is empty");
		}
		if (document.getFileSize() != null && document.getFileSize() != bytes.length) {
			throw new BadRequestException("AI document content size does not match the stored document metadata");
		}
		if ("application/pdf".equalsIgnoreCase(document.getFileType())) {
			AiDocumentResult aggregated = resolveTransactionAssessment(resolveCompanyRole(aggregatePageResults(analyzePdfPages(document.getOriginalFileName(), bytes,
					typeCodes, categoryCodes, company)), company));
			documentService.updateProcessingStatus(documentId, DocumentStatus.PROCESSING);
			try {
				ValidatedAiDocumentResult validated = resultValidator.validate(aggregated, allowedTypes);
				boolean requiresReview = persistenceService.persist(documentId, validated);
				DocumentStatus status = requiresReview ? DocumentStatus.NEED_REVIEW : DocumentStatus.PROCESSED;
				return new AiDocumentProcessingResponse(documentId, status, requiresReview, validated.warnings(), null);
			} catch (RuntimeException exception) {
				documentService.updateProcessingStatus(documentId, DocumentStatus.FAILED);
				processingLogService.append(documentId, "AI_DOCUMENT", "FAILED", "FAILED", BigDecimal.ZERO, null, sanitize(exception.getMessage()));
				throw exception;
			}
		}
		List<String> preprocessingWarnings = new ArrayList<>();
		ImagePreprocessingResult preprocessingResult = null;
		String aiContentType = document.getFileType();
		try {
			preprocessingResult = imagePreprocessingService.preprocess(bytes, document.getFileType());
			if (preprocessingResult.applied()) {
				String processedKey = documentService.storeProcessedImage(documentId, preprocessingResult.bytes(), preprocessingResult.contentType());
				bytes = preprocessingResult.bytes();
				aiContentType = preprocessingResult.contentType();
				processingLogService.append(documentId, "IMAGE_PREPROCESSING", "NORMALIZE", "SUCCESS", null, preprocessingResult.durationMs(),
						"angle=%.2f; original=%dx%d; processed=%dx%d; objectKey=%s".formatted(preprocessingResult.detectedAngleDegrees(), preprocessingResult.originalWidth(), preprocessingResult.originalHeight(), preprocessingResult.processedWidth(), preprocessingResult.processedHeight(), processedKey));
			}
		} catch (RuntimeException exception) {
			preprocessingWarnings.add("Image preprocessing failed; original image was sent to AI");
			processingLogService.append(documentId, "IMAGE_PREPROCESSING", "NORMALIZE", "WARNING", null, null, "Preprocessing failed: " + sanitize(exception.getMessage()));
		}
		log.info("AI document input documentId={} mimeType={} storedBytes={} minioBytes={} allowedTypeCount={}",
				documentId, document.getFileType(), document.getFileSize(), bytes.length, typeCodes.size());
		documentService.updateProcessingStatus(documentId, DocumentStatus.PROCESSING);
		processingLogService.append(documentId, "AI_DOCUMENT", "START", "PROCESSING", null, null,
				reprocess ? "AI reprocess requested" : "AI process requested");

		try {
			AiDocumentResult result = resolveTransactionAssessment(resolveCompanyRole(analyzeSingleImage(document.getOriginalFileName(), aiContentType, bytes,
					typeCodes, categoryCodes, company), company));
			ValidatedAiDocumentResult validated = resultValidator.validate(result, allowedTypes);
			boolean requiresReview = persistenceService.persist(documentId, validated);
			DocumentStatus status = requiresReview ? DocumentStatus.NEED_REVIEW : DocumentStatus.PROCESSED;
			List<String> warnings = new ArrayList<>(validated.warnings());
			warnings.addAll(preprocessingWarnings);
			ImagePreprocessingMetadata metadata = preprocessingResult == null ? null : new ImagePreprocessingMetadata(preprocessingResult.applied(), preprocessingResult.detectedAngleDegrees(), preprocessingResult.originalWidth(), preprocessingResult.originalHeight(), preprocessingResult.processedWidth(), preprocessingResult.processedHeight(), preprocessingResult.durationMs(), preprocessingResult.warning());
			return new AiDocumentProcessingResponse(documentId, status, requiresReview, warnings, metadata);
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

	/** Executes only the image analysis boundary. Validation and document persistence remain in process(). */
	private AiDocumentResult analyzeSingleImage(String fileName, String contentType, byte[] bytes,
			List<String> documentTypes, List<String> categoryCodes, Company company) {
		AiDocumentRequest request = new AiDocumentRequest(fileName, contentType, bytes, documentTypes,
				AiDocumentPromptFactory.create(documentTypes, categoryCodes, company == null ? null : company.getCompanyName(),
						company == null ? null : company.getTaxCode()));
		return aiProcessingService.analyzeDocument(request);
	}

	private List<AiDocumentResult> analyzePdfPages(String fileName, byte[] pdfBytes, List<String> documentTypes,
			List<String> categoryCodes, Company company) {
		List<AiDocumentResult> results = new ArrayList<>();
		for (PdfPageImage page : pdfImageConversionService.convert(pdfBytes)) {
			results.add(analyzeSingleImage(fileName + "#page-" + page.pageNumber(), page.contentType(), page.bytes(), documentTypes, categoryCodes, company));
		}
		return List.copyOf(results);
	}

	private AiDocumentResult aggregatePageResults(List<AiDocumentResult> pages) {
		if (pages == null || pages.isEmpty()) throw new BadRequestException("PDF has no analyzable pages");
		AiDocumentResult first = pages.get(0);
		String rawText = pages.stream().map(AiDocumentResult::rawText).filter(value -> value != null && !value.isBlank())
				.collect(java.util.stream.Collectors.joining("\n\n"));
		List<AiDocumentResult.AiInvoiceItemExtraction> items = pages.stream().filter(page -> page.invoice() != null && page.invoice().items() != null)
				.flatMap(page -> page.invoice().items().stream()).toList();
		AiDocumentResult.AiInvoiceExtraction invoice = first.invoice() == null ? null : new AiDocumentResult.AiInvoiceExtraction(first.invoice().invoiceNumber(), first.invoice().invoiceSeries(), first.invoice().invoiceDate(), first.invoice().sellerName(), first.invoice().sellerTaxCode(), first.invoice().sellerAddress(), first.invoice().buyerName(), first.invoice().buyerTaxCode(), first.invoice().buyerAddress(), first.invoice().subtotal(), first.invoice().vatAmount(), first.invoice().totalAmount(), items);
		BigDecimal confidence = pages.stream().map(AiDocumentResult::classificationConfidence).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(pages.size()), java.math.RoundingMode.HALF_UP);
		AiDocumentResult.DocumentDirection direction = pages.stream().map(AiDocumentResult::documentDirection)
				.filter(java.util.Objects::nonNull).findFirst().orElse(AiDocumentResult.DocumentDirection.UNKNOWN);
		AiDocumentResult.AiTransactionAssessment assessment = pages.stream().map(AiDocumentResult::transactionAssessment)
				.filter(java.util.Objects::nonNull)
				.filter(value -> value.type() != AiDocumentResult.TransactionAssessmentType.UNKNOWN)
				.findFirst().orElseGet(() -> pages.stream().map(AiDocumentResult::transactionAssessment)
						.filter(java.util.Objects::nonNull).findFirst()
						.orElse(new AiDocumentResult.AiTransactionAssessment(AiDocumentResult.TransactionAssessmentType.UNKNOWN, null, null)));
		AiDocumentResult.AiCompanyRole companyRole = pages.stream().map(AiDocumentResult::companyRole)
				.filter(java.util.Objects::nonNull)
				.filter(value -> value.role() != AiDocumentResult.CompanyRole.UNKNOWN)
				.findFirst().orElseGet(() -> pages.stream().map(AiDocumentResult::companyRole)
						.filter(java.util.Objects::nonNull).findFirst()
						.orElse(new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.UNKNOWN, null, null)));
		return new AiDocumentResult(first.provider(), first.model(), first.documentType(), confidence, first.accountingCategoryCode(), first.accountingAccount(), rawText, invoice, pages.stream().flatMap(page -> page.fields() == null ? java.util.stream.Stream.empty() : page.fields().stream()).toList(), pages.stream().flatMap(page -> page.warnings() == null ? java.util.stream.Stream.empty() : page.warnings().stream()).toList(), first.rawResponse(), pages.stream().mapToLong(AiDocumentResult::durationMs).sum(), direction, assessment, pages.stream().flatMap(page -> page.extraFields() == null ? java.util.stream.Stream.empty() : page.extraFields().stream()).toList(), companyRole);
	}

	/** Exact server-side tax-code matches take precedence over the AI suggestion. */
	private AiDocumentResult resolveCompanyRole(AiDocumentResult result, Company company) {
		AiDocumentResult.AiCompanyRole suggestedRole = result.companyRole() == null
				? new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.UNKNOWN, null, null) : result.companyRole();
		AiDocumentResult.AiCompanyRole resolvedRole = suggestedRole;
		String companyTaxCode = normalizeTaxCode(company == null ? null : company.getTaxCode());
		AiDocumentResult.AiInvoiceExtraction invoice = result.invoice();
		if (companyTaxCode != null && invoice != null) {
			boolean sellerMatch = companyTaxCode.equals(normalizeTaxCode(invoice.sellerTaxCode()));
			boolean buyerMatch = companyTaxCode.equals(normalizeTaxCode(invoice.buyerTaxCode()));
			if (sellerMatch && buyerMatch) {
				resolvedRole = new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.UNKNOWN, BigDecimal.ONE,
						"Current company tax code matches both seller and buyer tax codes");
			} else if (sellerMatch) {
				resolvedRole = new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.SELLER, BigDecimal.ONE,
						"Current company tax code exactly matches seller tax code");
			} else if (buyerMatch) {
				resolvedRole = new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.BUYER, BigDecimal.ONE,
						"Current company tax code exactly matches buyer tax code");
			}
		}
		return result.withCompanyRoleAndDirection(resolvedRole, directionFor(resolvedRole, result.documentDirection()));
	}

	private AiDocumentResult.DocumentDirection directionFor(AiDocumentResult.AiCompanyRole companyRole,
			AiDocumentResult.DocumentDirection suggestedDirection) {
		return switch (companyRole.role()) {
			case SELLER, ISSUER -> AiDocumentResult.DocumentDirection.OUTGOING;
			case BUYER, RECIPIENT -> AiDocumentResult.DocumentDirection.INCOMING;
			case INTERNAL -> AiDocumentResult.DocumentDirection.INTERNAL;
			case UNRELATED, UNKNOWN -> suggestedDirection == null ? AiDocumentResult.DocumentDirection.UNKNOWN : suggestedDirection;
		};
	}

	private String normalizeTaxCode(String value) {
		if (value == null) return null;
		String normalized = value.replaceAll("[\\s\\u00A0]+", "").trim();
		return normalized.isBlank() ? null : normalized;
	}

	/** Normalizes the AI suggestion after company-role resolution; it never writes financial records. */
	private AiDocumentResult resolveTransactionAssessment(AiDocumentResult result) {
		AiDocumentResult.AiCompanyRole companyRole = result.companyRole() == null
				? new AiDocumentResult.AiCompanyRole(AiDocumentResult.CompanyRole.UNKNOWN, null, null) : result.companyRole();
		String typeCode = normalizeDocumentType(result.documentType());
		if (companyRole.role() == AiDocumentResult.CompanyRole.UNRELATED) {
			return result.withTransactionAssessment(unknownAssessment("Document is unrelated to the current company"));
		}
		if (isInvoiceType(typeCode)) {
			if (companyRole.role() == AiDocumentResult.CompanyRole.BUYER) {
				return result.withTransactionAssessment(deterministicAssessment(AiDocumentResult.TransactionAssessmentType.EXPENSE,
						"Current company is identified as invoice buyer"));
			}
			if (companyRole.role() == AiDocumentResult.CompanyRole.SELLER) {
				return result.withTransactionAssessment(deterministicAssessment(AiDocumentResult.TransactionAssessmentType.INCOME,
						"Current company is identified as invoice seller"));
			}
		}
		if (isPaymentVoucher(typeCode)) {
			return result.withTransactionAssessment(deterministicAssessment(AiDocumentResult.TransactionAssessmentType.EXPENSE,
					"Document type is PAYMENT_VOUCHER for the current company"));
		}
		if (isReceiptVoucher(typeCode)) {
			return result.withTransactionAssessment(deterministicAssessment(AiDocumentResult.TransactionAssessmentType.INCOME,
					"Document type is RECEIPT_VOUCHER for the current company"));
		}
		AiDocumentResult.AiTransactionAssessment suggestion = result.transactionAssessment();
		if (isInternalTransfer(typeCode, companyRole, suggestion)) {
			return result.withTransactionAssessment(deterministicAssessment(AiDocumentResult.TransactionAssessmentType.TRANSFER,
					"Internal transfer evidence is present in the document context"));
		}
		if (suggestion != null && suggestion.type() != AiDocumentResult.TransactionAssessmentType.UNKNOWN
				&& suggestion.confidence() != null && suggestion.confidence().compareTo(new BigDecimal("0.60")) >= 0) {
			return result.withTransactionAssessment(suggestion);
		}
		return result.withTransactionAssessment(unknownAssessment("Insufficient company and document evidence for a financial assessment"));
	}

	private AiDocumentResult.AiTransactionAssessment deterministicAssessment(
			AiDocumentResult.TransactionAssessmentType type, String reason) {
		return new AiDocumentResult.AiTransactionAssessment(type, BigDecimal.ONE, reason);
	}

	private AiDocumentResult.AiTransactionAssessment unknownAssessment(String reason) {
		return new AiDocumentResult.AiTransactionAssessment(AiDocumentResult.TransactionAssessmentType.UNKNOWN,
				BigDecimal.ZERO, reason);
	}

	private String normalizeDocumentType(String value) {
		if (value == null) return "";
		return value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
	}

	private boolean isInvoiceType(String typeCode) {
		return "INVOICE".equals(typeCode) || "VAT_INVOICE".equals(typeCode) || typeCode.endsWith("_INVOICE");
	}

	private boolean isPaymentVoucher(String typeCode) {
		return "PAYMENT_VOUCHER".equals(typeCode) || "PAYMENT_SLIP".equals(typeCode);
	}

	private boolean isReceiptVoucher(String typeCode) {
		return "RECEIPT_VOUCHER".equals(typeCode) || "RECEIPT_SLIP".equals(typeCode);
	}

	private boolean isInternalTransfer(String typeCode, AiDocumentResult.AiCompanyRole companyRole,
			AiDocumentResult.AiTransactionAssessment suggestion) {
		return companyRole.role() == AiDocumentResult.CompanyRole.INTERNAL
				&& (typeCode.contains("TRANSFER") || (suggestion != null
						&& suggestion.type() == AiDocumentResult.TransactionAssessmentType.TRANSFER
						&& suggestion.confidence() != null && suggestion.confidence().compareTo(new BigDecimal("0.60")) >= 0));
	}

	private String sanitize(String message) {
		return message == null || message.isBlank() ? "AI document processing failed" : message.substring(0, Math.min(message.length(), 500));
	}
}
