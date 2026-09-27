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
import com.example.invoice.repository.DocumentTypeRepository;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.OCRResultRepository;
import com.example.invoice.config.AiProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists a validated result after the remote AI request has completed. */
@Service
@RequiredArgsConstructor
public class DocumentAiResultPersistenceService {
	private final DocumentService documentService;
	private final DocumentTypeRepository documentTypeRepository;
	private final OCRResultRepository ocrResultRepository;
	private final ClassificationRepository classificationRepository;
	private final InvoiceRepository invoiceRepository;
	private final ExtractedFieldRepository extractedFieldRepository;
	private final ProcessingLogService processingLogService;
	private final AiProperties aiProperties;

	@Transactional
	public boolean persist(Long documentId, ValidatedAiDocumentResult validated) {
		Document document = documentService.load(documentId);
		AiDocumentResult result = validated.result();
		DocumentType type = documentTypeRepository.findByCode(result.documentType())
				.orElseThrow(() -> new IllegalStateException("Validated document type no longer exists"));
		document.setType(type);
		document.setDocumentType(type.getCode());

		persistOcrWhenPresent(document, result, validated.confidence());
		boolean requiresReview = validated.requiresReview(aiProperties.getDocument().getReviewThreshold());
		persistClassification(document, result, validated.confidence(), validated.warnings(), requiresReview);
		if (result.invoice() != null) {
			persistInvoice(document, result.invoice(), result.fields());
		}

		document.setStatus(requiresReview ? DocumentStatus.NEED_REVIEW : DocumentStatus.PROCESSED);
		document.setReviewStatus(ReviewStatus.PENDING);
		processingLogService.append(documentId, "AI_DOCUMENT", "PERSIST", "SUCCESS", validated.confidence(),
				result.durationMs(), String.join("; ", validated.warnings()), result.provider(), result.model());
		return requiresReview;
	}

	private void persistOcrWhenPresent(Document document, AiDocumentResult result, BigDecimal confidence) {
		if (result.rawText() == null || result.rawText().isBlank()) return;
		OCRResult ocr = ocrResultRepository.findFirstByDocumentIdOrderByProcessedAtDesc(document.getId()).orElseGet(OCRResult::new);
		ocr.setDocument(document);
		ocr.setOcrEngine(result.provider());
		ocr.setModelVersion(result.model());
		ocr.setRawText(result.rawText());
		ocr.setConfidence(confidence);
		ocr.setProcessingTime(result.durationMs());
		ocr.setStatus("SUCCESS");
		ocrResultRepository.save(ocr);
	}

	private void persistClassification(Document document, AiDocumentResult result, BigDecimal confidence, List<String> warnings, boolean requiresReview) {
		Classification classification = classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(document.getId())
				.orElseGet(Classification::new);
		classification.setDocument(document);
		classification.setModelName(result.provider());
		classification.setModelVersion(result.model());
		classification.setPredictedLabel(result.documentType());
		classification.setCategory(result.documentType());
		classification.setConfidence(confidence);
		classification.setReason(String.join("; ", warnings));
		classification.setStatus(requiresReview ? ClassificationStatus.NEED_REVIEW : ClassificationStatus.CLASSIFIED);
		classification.setAiGenerated(true);
		classificationRepository.save(classification);
	}

	private void persistInvoice(Document document, AiDocumentResult.AiInvoiceExtraction extraction,
			List<AiDocumentResult.AiExtractedField> fields) {
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
		invoice = invoiceRepository.save(invoice);
		extractedFieldRepository.deleteAll(extractedFieldRepository.findByInvoiceId(invoice.getId()));
		for (AiDocumentResult.AiExtractedField field : fields == null
				? List.<AiDocumentResult.AiExtractedField>of() : fields) {
			if (field.fieldName() == null || field.fieldName().isBlank()) continue;
			ExtractedField entity = new ExtractedField();
			entity.setInvoice(invoice);
			entity.setFieldName(field.fieldName());
			entity.setFieldValue(field.fieldValue());
			entity.setSource("AI");
			entity.setConfidence(field.confidence());
			extractedFieldRepository.save(entity);
		}
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
