package com.example.invoice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import com.example.invoice.ai.AiDocumentResultValidator;
import com.example.invoice.config.AiProperties;
import com.example.invoice.dto.ai.AiDocumentResult;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.entity.Invoice;
import com.example.invoice.repository.ClassificationRepository;
import com.example.invoice.repository.DocumentTypeRepository;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.InvoiceRepository;
import com.example.invoice.repository.OCRResultRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class DocumentAiResultPersistenceServiceTest {
	@Mock private DocumentService documentService;
	@Mock private DocumentTypeRepository documentTypeRepository;
	@Mock private OCRResultRepository ocrResultRepository;
	@Mock private ClassificationRepository classificationRepository;
	@Mock private InvoiceRepository invoiceRepository;
	@Mock private ExtractedFieldRepository extractedFieldRepository;
	@Mock private ProcessingLogService processingLogService;

	@Test
	void reprocessUpdatesTheExistingInvoiceInsteadOfCreatingAnotherOne() {
		Document document = new Document();
		document.setId(7L);
		DocumentType type = new DocumentType();
		type.setCode("INVOICE");
		Invoice existing = new Invoice();
		existing.setId(11L);
		AiDocumentResult result = new AiDocumentResult("ollama", "vision", "INVOICE", new BigDecimal("0.95"),
				null, new AiDocumentResult.AiInvoiceExtraction("0001", null, "2026-09-27", null, null, null,
					null, null, null, new BigDecimal("100"), new BigDecimal("10"), new BigDecimal("110"), List.of()),
				List.of(), List.of(), "raw", 12L);
		var validated = new AiDocumentResultValidator().validate(result, Set.of("INVOICE"));

		when(documentService.load(7L)).thenReturn(document);
		when(documentTypeRepository.findByCode("INVOICE")).thenReturn(Optional.of(type));
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());
		when(invoiceRepository.findByDocumentId(7L)).thenReturn(Optional.of(existing));
		when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
		DocumentAiResultPersistenceService service = new DocumentAiResultPersistenceService(documentService,
				documentTypeRepository, ocrResultRepository, classificationRepository, invoiceRepository,
				extractedFieldRepository, processingLogService, new AiProperties());
		service.persist(7L, validated);

		verify(invoiceRepository).save(same(existing));
	}

	@Test
	void reprocessFromAiInvoiceToNonInvoiceRemovesOnlyAiGeneratedInvoice() {
		Document document = new Document(); document.setId(8L);
		DocumentType type = new DocumentType(); type.setCode("OTHER");
		Invoice stale = new Invoice(); stale.setId(12L); stale.setAiGenerated(true);
		AiDocumentResult result = new AiDocumentResult("ollama", "text", "OTHER", new BigDecimal("0.95"),
				"receipt text", null, List.of(), List.of(), "raw", 10L);
		var validated = new AiDocumentResultValidator().validate(result, Set.of("OTHER"));
		when(documentService.load(8L)).thenReturn(document);
		when(documentTypeRepository.findByCode("OTHER")).thenReturn(Optional.of(type));
		when(invoiceRepository.findByDocumentId(8L)).thenReturn(Optional.of(stale));
		when(extractedFieldRepository.findByDocumentId(8L)).thenReturn(List.of());
		when(classificationRepository.findFirstByDocumentIdOrderByCreatedAtDesc(8L)).thenReturn(Optional.empty());
		DocumentAiResultPersistenceService service = new DocumentAiResultPersistenceService(documentService,
				documentTypeRepository, ocrResultRepository, classificationRepository, invoiceRepository,
				extractedFieldRepository, processingLogService, new AiProperties());
		service.persist(8L, validated);
		verify(invoiceRepository).deleteAiGeneratedByDocumentId(8L);
		verify(invoiceRepository, never()).save(stale);
	}
}
