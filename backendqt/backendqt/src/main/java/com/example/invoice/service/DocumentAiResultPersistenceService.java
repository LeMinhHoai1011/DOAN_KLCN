package com.example.invoice.service;

import com.example.invoice.ai.AiDocumentResultValidator.ValidatedAiDocumentResult;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.entity.Classification;
import com.example.invoice.entity.ClassificationStatus;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.Invoice;
import com.example.invoice.entity.InvoiceItem;
import com.example.invoice.entity.OCRResult;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.AccountingCategoryRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.OCRResultRepository;
import com.example.invoice.config.AiProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists a validated result after the remote AI request has completed. */
@Service
public class DocumentAiResultPersistenceService {
	private static final Set<String> RESERVED_CORE_FIELD_NAMES = Set.of(
			"invoiceNumber", "invoiceSeries", "invoiceDate", "sellerName", "sellerTaxCode", "sellerAddress",
			"buyerName", "buyerTaxCode", "buyerAddress", "subtotal", "vatAmount", "taxAmount", "totalAmount", "items");
	private static final int MAX_EXTRACTED_FIELD_NAME_LENGTH = 100;
	private final DocumentService documentService;
	private final DocumentTypeRepository documentTypeRepository;
	private final OCRResultRepository ocrResultRepository;
	private final ClassificationRepository classificationRepository;
	private final InvoiceRepository invoiceRepository;
	private final ExtractedFieldRepository extractedFieldRepository;
	private final ProcessingLogService processingLogService;
	private final AiProperties aiProperties;
	private final AccountingCategoryRepository accountingCategoryRepository;
	private final ObjectMapper objectMapper;

	@Autowired
	public DocumentAiResultPersistenceService(DocumentService documentService, DocumentTypeRepository documentTypeRepository,
			OCRResultRepository ocrResultRepository, ClassificationRepository classificationRepository, InvoiceRepository invoiceRepository,
			ExtractedFieldRepository extractedFieldRepository, ProcessingLogService processingLogService, AiProperties aiProperties,
			AccountingCategoryRepository accountingCategoryRepository, ObjectMapper objectMapper) {
		this.documentService = documentService;
		this.documentTypeRepository = documentTypeRepository;
		this.ocrResultRepository = ocrResultRepository;
		this.classificationRepository = classificationRepository;
		this.invoiceRepository = invoiceRepository;
		this.extractedFieldRepository = extractedFieldRepository;
		this.processingLogService = processingLogService;
		this.aiProperties = aiProperties;
		this.accountingCategoryRepository = accountingCategoryRepository;
		this.objectMapper = objectMapper;
	}

	DocumentAiResultPersistenceService(DocumentService documentService, DocumentTypeRepository documentTypeRepository,
			OCRResultRepository ocrResultRepository, ClassificationRepository classificationRepository, InvoiceRepository invoiceRepository,
			ExtractedFieldRepository extractedFieldRepository, ProcessingLogService processingLogService, AiProperties aiProperties) {
		this(documentService, documentTypeRepository, ocrResultRepository, classificationRepository, invoiceRepository,
				extractedFieldRepository, processingLogService, aiProperties, null, new ObjectMapper());
	}

	@Transactional
	public boolean persist(Long documentId, ValidatedAiDocumentResult validated) {
		return persist(documentId, validated, null);
	}

	@Transactional
	public boolean persist(Long documentId, ValidatedAiDocumentResult validated, OcrDocumentResult tessOcr) {
		Document document = documentService.load(documentId);
		AiDocumentResult result = validated.result();
		DocumentType type = documentTypeRepository.findByCode(result.documentType())
				.orElseThrow(() -> new IllegalStateException("Loại chứng từ đã xác thực không còn tồn tại"));
		if (!type.isActive()) throw new IllegalStateException("Loại chứng từ đã xác thực không còn hoạt động");
		document.setType(type);
		document.setDocumentType(type.getCode());
		persistIntelligence(document, result);

		persistOcrWhenPresent(document, result, validated.confidence(), tessOcr);
		boolean requiresReview = validated.requiresReview(aiProperties.getDocument().getReviewThreshold());
		persistClassification(document, result, validated.confidence(), validated.warnings(), requiresReview);
		processingLogService.append(documentId, "CLASSIFICATION", "CLASSIFY", "SUCCESS", validated.confidence(),
				result.durationMs(), "documentType=" + result.documentType(), result.provider(), result.model());
		Invoice invoice = null;
		if (isInvoiceType(type.getCode()) && result.invoice() != null) {
			invoice = persistInvoice(document, result.invoice());
		} else if (!isInvoiceType(type.getCode())) {
			removeStaleInvoice(document);
		}
		replaceAiExtractedFields(document, invoice, result.fields(), result.extraFields());

		document.setStatus(requiresReview ? DocumentStatus.NEED_REVIEW : DocumentStatus.PROCESSED);
		document.setReviewStatus(ReviewStatus.PENDING);
		processingLogService.append(documentId, "PERSISTENCE", "PERSIST", "SUCCESS", validated.confidence(),
				result.durationMs(), String.join("; ", validated.warnings()), result.provider(), result.model());
		return requiresReview;
	}

	/** Commits OCR independently so later AI or validation failures cannot roll it back. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void persistOcr(Long documentId, OcrDocumentResult tessOcr) {
		if (tessOcr == null || !tessOcr.successful()) return;
		Document document = documentService.load(documentId);
		persistOcrWhenPresent(document, null,
				BigDecimal.valueOf(tessOcr.confidence()).movePointLeft(2), tessOcr);
	}

	private void persistOcrWhenPresent(Document document, AiDocumentResult result, BigDecimal confidence, OcrDocumentResult tessOcr) {
		String rawText = tessOcr != null && tessOcr.successful() ? tessOcr.rawText() : result == null ? null : result.rawText();
		if (rawText == null || rawText.isBlank()) return;
		OCRResult ocr = ocrResultRepository.findFirstByDocumentIdOrderByProcessedAtDesc(document.getId()).orElseGet(OCRResult::new);
		ocr.setDocument(document);
		ocr.setOcrEngine(tessOcr != null && tessOcr.successful() ? "tesseract" : result.provider());
		ocr.setModelVersion(tessOcr != null && tessOcr.successful() ? aiProperties.getOcr().getLanguage() : result.model());
		ocr.setLanguage(tessOcr != null && tessOcr.successful()
				? tessOcr.pages().stream().map(OcrPageResult::language).filter(java.util.Objects::nonNull).findFirst().orElse(aiProperties.getOcr().getLanguage())
				: null);
		ocr.setSourceType(tessOcr != null && tessOcr.successful() ? "TESS4J_WORD_LAYOUT" : "AI");
		ocr.setRawText(rawText);
		ocr.setConfidence(tessOcr != null && tessOcr.successful() ? BigDecimal.valueOf(tessOcr.confidence()).movePointLeft(2) : confidence);
		ocr.setProcessingTime(tessOcr != null && tessOcr.successful() ? tessOcr.durationMs() : result.durationMs());
		ocr.setLayoutJson(tessOcr == null ? null : serializeLayout(tessOcr));
		ocr.setStatus("SUCCESS");
		ocrResultRepository.save(ocr);
	}

	private String serializeLayout(OcrDocumentResult result) {
		try {
			List<Map<String, Object>> pages = result.pages().stream().map(page -> {
				List<Map<String, Object>> words = page.words().stream().map(word -> Map.<String, Object>of(
						"id", word.id(), "text", word.text(), "confidence", word.confidence(),
						"x", Math.round(word.x() * page.imageWidth()), "y", Math.round(word.y() * page.imageHeight()),
						"width", Math.round(word.width() * page.imageWidth()), "height", Math.round(word.height() * page.imageHeight()))).toList();
				Map<String, Object> value = new LinkedHashMap<>();
				value.put("page", page.pageNumber()); value.put("width", page.imageWidth()); value.put("height", page.imageHeight());
				value.put("language", page.language()); value.put("words", words);
				return value;
			}).toList();
			return objectMapper.writeValueAsString(Map.of("version", 1, "pages", pages));
		}
		catch (JsonProcessingException exception) { throw new IllegalStateException("Không thể tuần tự hóa bố cục OCR", exception); }
	}

	private void persistClassification(Document document, AiDocumentResult result, BigDecimal confidence, List<String> warnings, boolean requiresReview) {
		Classification classification = classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(document.getId())
				.orElseGet(Classification::new);
		classification.setDocument(document);
		classification.setModelName(result.provider());
		classification.setModelVersion(result.model());
		classification.setPredictedLabel(result.documentType());
		classification.setCategory(result.accountingCategoryCode());
		if (accountingCategoryRepository != null && result.accountingCategoryCode() != null && document.getCompany() != null) {
			accountingCategoryRepository.findByCompanyId(document.getCompany().getId()).stream()
					.filter(category -> result.accountingCategoryCode().equals(category.getCategoryCode()))
					.findFirst().ifPresent(classification::setAccountingCategory);
		}
		classification.setConfidence(confidence);
		classification.setReason(String.join("; ", warnings));
		classification.setStatus(requiresReview ? ClassificationStatus.NEED_REVIEW : ClassificationStatus.CLASSIFIED);
		classification.setAiGenerated(true);
		classificationRepository.save(classification);
	}

	private Invoice persistInvoice(Document document, AiDocumentResult.AiInvoiceExtraction extraction) {
		Invoice invoice = invoiceRepository.findByDocumentId(document.getId()).orElseGet(Invoice::new);
		invoice.setDocument(document);
		invoice.setInvoiceNumber(extraction.invoiceNumber());
		invoice.setInvoiceSeries(extraction.invoiceSeries());
		invoice.setInvoiceDate(parseDate(extraction.invoiceDate()));
		invoice.setSellerName(extraction.sellerName());
		invoice.setSellerTaxCode(extraction.sellerTaxCode());
		invoice.setSellerAddress(extraction.sellerAddress());
		invoice.setBuyerName(extraction.buyerName());
		invoice.setBuyerTaxCode(extraction.buyerTaxCode());
		invoice.setBuyerAddress(extraction.buyerAddress());
		invoice.setSubtotal(extraction.subtotal());
		invoice.setVatAmount(extraction.vatAmount());
		invoice.setTotalAmount(extraction.totalAmount());
		invoice.setAiGenerated(true);
		invoice.getItems().clear();
		if (extraction.items() != null) {
			for (AiDocumentResult.AiInvoiceItemExtraction item : extraction.items()) {
				if (item.productName() == null || item.productName().isBlank()) continue;
				InvoiceItem entity = new InvoiceItem();
				entity.setInvoice(invoice);
				entity.setProductName(item.productName());
				entity.setQuantity(item.quantity());
				entity.setUnit(item.unit());
				entity.setUnitPrice(item.unitPrice());
				entity.setTaxRate(item.taxRate());
				entity.setTaxAmount(item.taxAmount());
				entity.setAmount(item.amount());
				invoice.getItems().add(entity);
			}
		}
		return invoiceRepository.save(invoice);
	}

	/** Removes obsolete AI type-specific data while retaining user-managed fields. */
	private void removeStaleInvoice(Document document) {
		Invoice invoice = invoiceRepository.findByDocumentId(document.getId()).orElse(null);
		if (invoice == null || !invoice.isAiGenerated()) return;
		for (ExtractedField field : extractedFieldRepository.findByDocumentId(document.getId())) {
			if (!"AI".equalsIgnoreCase(field.getSource()) && field.getInvoice() != null) {
				field.setInvoice(null);
				invoice.getExtractedFields().remove(field);
			}
		}
		invoiceRepository.deleteAiGeneratedByDocumentId(document.getId());
	}

	private boolean isInvoiceType(String typeCode) {
		if (typeCode == null) return false;
		String normalized = typeCode.trim().toUpperCase(java.util.Locale.ROOT)
				.replace('-', '_').replace(' ', '_');
		return "INVOICE".equals(normalized) || "VAT_INVOICE".equals(normalized)
				|| normalized.endsWith("_INVOICE");
	}

	private void replaceAiExtractedFields(Document document, Invoice invoice, List<AiDocumentResult.AiExtractedField> fields,
			List<AiDocumentResult.AiExtraField> extraFields) {
		extractedFieldRepository.deleteByDocumentIdAndSource(document.getId(), "AI");
		Set<String> persistedNames = new LinkedHashSet<>();
		for (AiDocumentResult.AiExtractedField field : fields == null
				? List.<AiDocumentResult.AiExtractedField>of() : fields) {
			if (field.fieldName() == null || field.fieldName().isBlank()) continue;
			persistedNames.add(normalizeExtraFieldName(field.fieldName()));
			persistExtractedField(document, invoice, field.fieldName(), field.fieldValue(), field.confidence());
		}
		for (NormalizedExtraField field : normalizeExtraFields(extraFields).values()) {
			if ((invoice != null && RESERVED_CORE_FIELD_NAMES.contains(field.name())) || persistedNames.contains(field.name())) continue;
			persistExtractedField(document, invoice, field.name(), field.value(), field.confidence());
			persistedNames.add(field.name());
		}
	}

	private Map<String, NormalizedExtraField> normalizeExtraFields(List<AiDocumentResult.AiExtraField> fields) {
		Map<String, NormalizedExtraField> normalized = new LinkedHashMap<>();
		if (fields == null) return normalized;
		for (AiDocumentResult.AiExtraField field : fields) {
			if (field == null || field.value() == null || field.value().isBlank()) continue;
			String name = normalizeExtraFieldName(field.name());
			if (name == null || name.length() > MAX_EXTRACTED_FIELD_NAME_LENGTH || !isValidConfidence(field.confidence())) continue;
			NormalizedExtraField candidate = new NormalizedExtraField(name, field.value().trim(), field.confidence());
			NormalizedExtraField current = normalized.get(name);
			if (current == null || isHigherConfidence(candidate.confidence(), current.confidence())) normalized.put(name, candidate);
		}
		return normalized;
	}

	private String normalizeExtraFieldName(String value) {
		if (value == null || value.isBlank()) return null;
		String trimmed = value.trim();
		if (trimmed.matches("[A-Za-z][A-Za-z0-9]*"))
			return Character.toLowerCase(trimmed.charAt(0)) + trimmed.substring(1);
		String[] parts = trimmed.replaceAll("[^A-Za-z0-9]+", " ").trim().split("\\s+");
		StringBuilder normalized = new StringBuilder();
		for (String part : parts) {
			if (part.isBlank()) continue;
			String lower = part.toLowerCase(java.util.Locale.ROOT);
			if (normalized.isEmpty()) normalized.append(lower);
			else normalized.append(Character.toUpperCase(lower.charAt(0))).append(lower.substring(1));
		}
		return normalized.isEmpty() || !Character.isLetter(normalized.charAt(0)) ? null : normalized.toString();
	}

	private boolean isValidConfidence(BigDecimal confidence) {
		return confidence == null || (confidence.compareTo(BigDecimal.ZERO) >= 0 && confidence.compareTo(BigDecimal.ONE) <= 0);
	}

	private boolean isHigherConfidence(BigDecimal candidate, BigDecimal current) {
		if (candidate == null) return false;
		return current == null || candidate.compareTo(current) > 0;
	}

	private void persistExtractedField(Document document, Invoice invoice, String name, String value, BigDecimal confidence) {
		ExtractedField entity = new ExtractedField();
		entity.setDocument(document);
		entity.setInvoice(invoice);
		entity.setFieldName(name);
		entity.setFieldValue(value);
		entity.setSource("AI");
		entity.setConfidence(confidence);
		extractedFieldRepository.save(entity);
	}

	private void persistIntelligence(Document document, AiDocumentResult result) {
		AiDocumentResult.AiCompanyRole companyRole = result.companyRole();
		document.setCompanyRole(companyRole == null || companyRole.role() == null ? null : companyRole.role().name());
		document.setCompanyRoleConfidence(companyRole == null ? null : companyRole.confidence());
		document.setCompanyRoleReason(companyRole == null ? null : companyRole.reason());
		document.setDocumentDirection(result.documentDirection() == null ? null : result.documentDirection().name());
		AiDocumentResult.AiTransactionAssessment assessment = result.transactionAssessment();
		document.setTransactionAssessmentType(assessment == null || assessment.type() == null ? null : assessment.type().name());
		document.setTransactionAssessmentConfidence(assessment == null ? null : assessment.confidence());
		document.setTransactionAssessmentReason(assessment == null ? null : assessment.reason());
	}

	private record NormalizedExtraField(String name, String value, BigDecimal confidence) {
	}

	private LocalDate parseDate(String value) {
		if (value == null || value.isBlank()) return null;
		try {
			return LocalDate.parse(value);
		} catch (DateTimeParseException exception) {
			return null;
		}
	}
}
