package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.example.invoice.dto.invoice.ExtractedFieldResponse;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.FieldCorrection;
import com.example.invoice.entity.User;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.FieldCorrectionRepository;
import com.example.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExtractedFieldEffectiveValueTest {
	@Test
	void reloadReturnsLatestCorrectionAndKeepsOriginalAiValueAndConfidence() {
		InvoiceRepository invoices = mock(InvoiceRepository.class);
		ExtractedFieldRepository fields = mock(ExtractedFieldRepository.class);
		FieldCorrectionRepository corrections = mock(FieldCorrectionRepository.class);
		DocumentService documents = mock(DocumentService.class);
		UserService users = mock(UserService.class);
		OcrFieldLocator locator = mock(OcrFieldLocator.class);
		InvoiceService service = new InvoiceService(invoices, fields, corrections, documents, users, locator);

		Document document = new Document();
		document.setId(17L);
		ExtractedField field = new ExtractedField();
		field.setId(29L);
		field.setDocument(document);
		field.setFieldName("deliveryAddress");
		field.setFieldValue("Duong Nguyen Van Linh");
		field.setSource("AI");
		field.setConfidence(new BigDecimal("0.93"));
		FieldCorrection correction = new FieldCorrection();
		correction.setNewValue("Đường Nguyễn Văn Linh");
		User correctedBy = new User();
		correctedBy.setId(8L);
		correction.setCorrectedBy(correctedBy);
		LocalDateTime correctedAt = LocalDateTime.of(2026, 10, 6, 10, 0);
		correction.setCreatedAt(correctedAt);

		when(documents.load(17L)).thenReturn(document);
		when(fields.findByDocumentId(17L)).thenReturn(List.of(field));
		when(corrections.findFirstByFieldIdOrderByCreatedAtDescIdDesc(29L)).thenReturn(Optional.of(correction));

		ExtractedFieldResponse response = service.findExtractedFieldsByDocumentId(17L).getFirst();

		assertEquals("Đường Nguyễn Văn Linh", response.fieldValue());
		assertEquals("Duong Nguyen Van Linh", response.originalAiValue());
		assertEquals("Đường Nguyễn Văn Linh", response.correctedValue());
		assertEquals(8L, response.correctedById());
		assertEquals(correctedAt, response.correctedAt());
		assertEquals(new BigDecimal("0.93"), response.confidence());
		assertEquals("Duong Nguyen Van Linh", field.getFieldValue());
		verify(corrections).findFirstByFieldIdOrderByCreatedAtDescIdDesc(29L);
	}
}
