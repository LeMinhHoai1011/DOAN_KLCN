package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.invoice.dto.document.RegionDtos.RegionProvenance;
import com.example.invoice.dto.document.RegionDtos.RegionRect;
import com.example.invoice.dto.document.RegionDtos.RegionSelectionBatchRequest;
import com.example.invoice.dto.document.RegionDtos.RegionSelectionRequest;
import com.example.invoice.dto.document.RegionDtos.RegionTextResponse;
import com.example.invoice.dto.invoice.ExtractedFieldCorrectionRequest;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.FieldCorrection;
import com.example.invoice.entity.AuditLog;
import com.example.invoice.entity.OCRResult;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.entity.Role;
import com.example.invoice.entity.User;
import com.example.invoice.entity.UserRoleAssignment;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.FieldCorrectionRepository;
import com.example.invoice.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.invoice.repository.OCRResultRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class RegionSelectionServiceTest {
	/** Two pages; page 1 holds a "Mã của CQT" line and a "Ngày ký" line below it, page 2 one word. */
	static final String LAYOUT = """
			{"version":2,"pages":[
			 {"page":1,"width":1000,"height":1400,"words":[
			  {"id":"w1","text":"Mã","confidence":0.95,"x":100,"y":200,"width":40,"height":20},
			  {"id":"w3","text":"CQT:","confidence":0.95,"x":200,"y":202,"width":50,"height":20},
			  {"id":"w2","text":"của","confidence":0.95,"x":145,"y":201,"width":45,"height":20},
			  {"id":"w4","text":"00A1B2C3D4E5F6","confidence":0.9,"x":260,"y":200,"width":200,"height":20},
			  {"id":"w5","text":"Ngày","confidence":0.9,"x":100,"y":240,"width":50,"height":20},
			  {"id":"w6","text":"05/03/2025","confidence":0.9,"x":160,"y":240,"width":120,"height":20},
			  {"id":"w7","text":"Ngoài","confidence":0.9,"x":700,"y":900,"width":80,"height":20}]},
			 {"page":2,"width":1000,"height":1400,"words":[
			  {"id":"p2","text":"Trang2","confidence":0.9,"x":100,"y":100,"width":100,"height":30}]}]}
			""";

	@Mock DocumentService documents;
	@Mock UserService users;
	@Mock OCRResultRepository ocrResults;
	@Mock AuditLogRepository selections;
	@Mock ExtractedFieldRepository extractedFields;
	private final ObjectMapper json = new ObjectMapper();

	private final Long documentId = 13L;
	private Document document;
	private User accountant;
	private RegionSelectionService service;

	@BeforeEach
	void setUp() {
		document = new Document();
		document.setId(documentId);
		document.setStatus(DocumentStatus.NEED_REVIEW);
		document.setReviewStatus(ReviewStatus.PENDING);
		accountant = actor("ACCOUNTANT");
		service = new RegionSelectionService(documents, users, ocrResults, selections, extractedFields);
		OCRResult ocr = new OCRResult();
		ocr.setLayoutJson(LAYOUT);
		lenient().when(users.loadCurrent(null)).thenReturn(accountant);
		lenient().when(documents.load(documentId)).thenReturn(document);
		lenient().when(documents.lockForEdit(documentId)).thenReturn(document);
		lenient().when(ocrResults.findFirstByDocumentIdOrderByProcessedAtDesc(documentId)).thenReturn(Optional.of(ocr));
		lenient().when(selections.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void returnsWordsInsideRegionInReadingOrderGroupedByLine() {
		RegionTextResponse response = service.extractText(documentId, new RegionRect(1, 90.0, 190.0, 400.0, 80.0), null);

		assertEquals(List.of("Mã của CQT: 00A1B2C3D4E5F6", "Ngày 05/03/2025"), response.lines());
		assertEquals("Mã của CQT: 00A1B2C3D4E5F6\nNgày 05/03/2025", response.text());
		assertEquals(List.of("w1", "w2", "w3", "w4", "w5", "w6"), response.words().stream().map(w -> w.id()).toList());
		assertEquals(1000, response.pageWidth());
		verify(documents).load(documentId);
	}

	@Test
	void keepsWordsOnlyWhenMostOfTheWordIsInside() {
		// Covers the code (x 260..460) fully but only 30% of "CQT:" (x 200..250).
		RegionTextResponse response = service.extractText(documentId, new RegionRect(1, 235.0, 195.0, 240.0, 30.0), null);
		assertEquals("00A1B2C3D4E5F6", response.text());
	}

	@Test
	void readsTheRequestedPageOnly() {
		assertEquals("Trang2", service.extractText(documentId, new RegionRect(2, 50.0, 50.0, 300.0, 200.0), null).text());
		assertEquals("", service.extractText(documentId, new RegionRect(1, 50.0, 50.0, 30.0, 30.0), null).text());
	}

	@Test
	void rejectsRegionsOutsideThePageTooSmallOrOnMissingPages() {
		assertThrows(BadRequestException.class, () -> service.extractText(documentId, new RegionRect(1, 900.0, 10.0, 200.0, 50.0), null));
		assertThrows(BadRequestException.class, () -> service.extractText(documentId, new RegionRect(1, 10.0, 10.0, 2.0, 50.0), null));
		assertThrows(BadRequestException.class, () -> service.extractText(documentId, new RegionRect(3, 10.0, 10.0, 50.0, 50.0), null));
		assertThrows(BadRequestException.class, () -> service.extractText(documentId, new RegionRect(1, Double.NaN, 10.0, 50.0, 50.0), null));
	}

	@Test
	void rejectsDocumentsWithoutLayout() {
		when(ocrResults.findFirstByDocumentIdOrderByProcessedAtDesc(documentId)).thenReturn(Optional.empty());
		assertThrows(BadRequestException.class, () -> service.extractText(documentId, new RegionRect(1, 10.0, 10.0, 50.0, 50.0), null));
	}

	@Test
	void employeeCannotReadOrRecordRegions() {
		when(users.loadCurrent(null)).thenReturn(actor("EMPLOYEE"));
		assertThrows(AccessDeniedException.class, () -> service.extractText(documentId, new RegionRect(1, 10.0, 10.0, 50.0, 50.0), null));
		assertThrows(AccessDeniedException.class, () -> service.recordInvoiceSelections(documentId, batch("signDate"), null));
		verifyNoInteractions(documents, selections);
	}

	@Test
	void documentOutsideCompanyScopeIsNotFound() {
		when(documents.load(documentId)).thenThrow(new ResourceNotFoundException("Không tìm thấy chứng từ"));
		assertThrows(ResourceNotFoundException.class, () -> service.extractText(documentId, new RegionRect(1, 10.0, 10.0, 50.0, 50.0), null));
		verifyNoInteractions(ocrResults);
	}

	@Test
	void recordsInvoiceFieldProvenanceAsManualRegion() throws Exception {
		var saved = service.recordInvoiceSelections(documentId, batch("taxAuthorityCode"), null);

		ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
		verify(selections).save(captor.capture());
		AuditLog event = captor.getValue();
		assertEquals("DOCUMENT_REGION_SELECTION", event.getEntityType());
		assertEquals("13", event.getEntityId());
		assertEquals("FIELD_REGION_SELECTION", event.getAction());
		assertSame(accountant, event.getUser());
		JsonNode selection = json.readTree(event.getNewValue());
		assertEquals("taxAuthorityCode", selection.path("fieldName").asText());
		assertEquals(1, selection.path("page").asInt());
		assertEquals(260.0, selection.path("x").asDouble());
		assertEquals("00A1B2C3D4E5F6", selection.path("finalValue").asText());
		assertEquals("MANUAL_REGION", selection.path("source").asText());
		assertEquals("MANUAL_REGION", saved.get(0).source());
	}

	@Test
	void rejectsUnknownInvoiceFieldsAndApprovedDocuments() {
		assertThrows(BadRequestException.class, () -> service.recordInvoiceSelections(documentId, batch("items"), null));
		document.setReviewStatus(ReviewStatus.APPROVED);
		assertThrows(BadRequestException.class, () -> service.recordInvoiceSelections(documentId, batch("signDate"), null));
		verify(selections, never()).save(any());
	}

	@Test
	void fieldCorrectionWithRegionIsSavedAsManualRegionCorrection() throws Exception {
		ExtractedFieldRepository fields = mock(ExtractedFieldRepository.class);
		FieldCorrectionRepository corrections = mock(FieldCorrectionRepository.class);
		ExtractedField field = new ExtractedField();
		field.setId(29L); field.setDocument(document); field.setFieldName("deliveryAddress");
		field.setFieldValue("Duong cu"); field.setSource("AI");
		when(fields.findById(29L)).thenReturn(Optional.of(field));
		when(corrections.save(any(FieldCorrection.class))).thenAnswer(invocation -> invocation.getArgument(0));
		ExtractedFieldCorrectionService correctionService = new ExtractedFieldCorrectionService(documents, users, fields, corrections);
		correctionService.setRegionSelections(service);

		correctionService.correct(documentId, 29L, new ExtractedFieldCorrectionRequest("Đường mới",
				new RegionProvenance(new RegionRect(1, 90.0, 190.0, 400.0, 30.0), "Duong moi")), null);

		ArgumentCaptor<FieldCorrection> correction = ArgumentCaptor.forClass(FieldCorrection.class);
		verify(corrections).save(correction.capture());
		assertEquals("MANUAL_REGION", correction.getValue().getReason());
		assertEquals("Duong cu", correction.getValue().getOldValue());
		assertEquals("Đường mới", correction.getValue().getNewValue());
		ArgumentCaptor<AuditLog> selection = ArgumentCaptor.forClass(AuditLog.class);
		verify(selections).save(selection.capture());
		JsonNode value = json.readTree(selection.getValue().getNewValue());
		assertEquals(29L, value.path("fieldId").asLong());
		assertEquals("deliveryAddress", value.path("fieldName").asText());
		assertEquals("Đường mới", value.path("finalValue").asText());
		assertEquals("Duong moi", value.path("extractedText").asText());
	}

	@Test
	void invalidRegionRollsBackTheCorrectionRequest() {
		ExtractedFieldRepository fields = mock(ExtractedFieldRepository.class);
		FieldCorrectionRepository corrections = mock(FieldCorrectionRepository.class);
		ExtractedField field = new ExtractedField();
		field.setId(29L); field.setDocument(document); field.setFieldName("deliveryAddress"); field.setSource("AI");
		when(fields.findById(29L)).thenReturn(Optional.of(field));
		ExtractedFieldCorrectionService correctionService = new ExtractedFieldCorrectionService(documents, users, fields, corrections);
		correctionService.setRegionSelections(service);

		assertThrows(BadRequestException.class, () -> correctionService.correct(documentId, 29L,
				new ExtractedFieldCorrectionRequest("x", new RegionProvenance(new RegionRect(9, 1.0, 1.0, 50.0, 50.0), "x")), null));
		verify(selections, never()).save(any());
	}

	@Test
	void plainCorrectionKeepsManualReason() {
		ExtractedFieldRepository fields = mock(ExtractedFieldRepository.class);
		FieldCorrectionRepository corrections = mock(FieldCorrectionRepository.class);
		ExtractedField field = new ExtractedField();
		field.setId(29L); field.setDocument(document); field.setFieldName("deliveryAddress"); field.setSource("AI");
		when(fields.findById(29L)).thenReturn(Optional.of(field));
		ExtractedFieldCorrectionService correctionService = new ExtractedFieldCorrectionService(documents, users, fields, corrections);
		correctionService.setRegionSelections(service);

		correctionService.correct(documentId, 29L, new ExtractedFieldCorrectionRequest("Giá trị"), null);
		ArgumentCaptor<FieldCorrection> correction = ArgumentCaptor.forClass(FieldCorrection.class);
		verify(corrections).save(correction.capture());
		assertEquals("Manual correction", correction.getValue().getReason());
		verifyNoInteractions(selections);
	}

	@Test
	void groupsSlightlyMisalignedWordsOnOneLine() {
		var lines = RegionSelectionService.groupLines(List.of(
				new com.example.invoice.dto.document.RegionDtos.RegionWord("b", "B", null, 60, 104, 40, 20),
				new com.example.invoice.dto.document.RegionDtos.RegionWord("a", "A", null, 10, 100, 40, 20),
				new com.example.invoice.dto.document.RegionDtos.RegionWord("c", "C", null, 10, 140, 40, 20)));
		assertEquals(2, lines.size());
		assertEquals(List.of("A", "B"), lines.get(0).stream().map(w -> w.text()).toList());
		assertTrue(lines.get(1).stream().allMatch(w -> w.text().equals("C")));
	}

	@Test
	void recordsLocationOnlyForAnAiFieldAndReturnsItAsManualLocation() throws Exception {
		ExtractedField field = new ExtractedField();
		field.setId(29L); field.setDocument(document); field.setFieldName("taxCode"); field.setSource("AI");
		when(extractedFields.findById(29L)).thenReturn(Optional.of(field));

		service.recordInvoiceSelections(documentId, new RegionSelectionBatchRequest(List.of(new RegionSelectionRequest(null,
				new RegionRect(1, 260.0, 200.0, 200.0, 20.0), "00A1B2C3D4E5F6", "0318334251", 29L))), null);

		ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
		verify(selections).save(captor.capture());
		JsonNode value = json.readTree(captor.getValue().getNewValue());
		assertEquals(29L, value.path("fieldId").asLong());
		assertEquals("taxCode", value.path("fieldName").asText());
		assertTrue(value.path("locationOnly").asBoolean());
		assertEquals(1000.0, value.path("pageWidth").asDouble());

		when(selections.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("DOCUMENT_REGION_SELECTION", "13"))
				.thenReturn(List.of(captor.getValue()));
		var location = service.manualLocations(documentId).get(29L);
		assertEquals(1, location.page());
		assertEquals(260, location.x());
		assertEquals(1000, location.pageWidth());
		assertEquals("MANUAL_REGION", location.source());
	}

	@Test
	void manualLocationUsesNewestEventAndFillsPageSizeForOlderEvents() {
		AuditLog newer = new AuditLog(); newer.setAction("FIELD_REGION_SELECTION");
		newer.setNewValue("{\"fieldId\":29,\"page\":2,\"x\":10,\"y\":20,\"width\":30,\"height\":40}");
		AuditLog older = new AuditLog(); older.setAction("FIELD_REGION_SELECTION");
		older.setNewValue("{\"fieldId\":29,\"page\":1,\"x\":1,\"y\":1,\"width\":9,\"height\":9,\"pageWidth\":1000,\"pageHeight\":1400}");
		AuditLog invoiceOnly = new AuditLog(); invoiceOnly.setAction("FIELD_REGION_SELECTION");
		invoiceOnly.setNewValue("{\"fieldName\":\"signDate\",\"page\":1,\"x\":1,\"y\":1,\"width\":9,\"height\":9}");
		when(selections.findByEntityTypeAndEntityIdOrderByCreatedAtDescIdDesc("DOCUMENT_REGION_SELECTION", "13"))
				.thenReturn(List.of(newer, older, invoiceOnly));

		var locations = service.manualLocations(documentId);
		assertEquals(1, locations.size());
		assertEquals(2, locations.get(29L).page());
		assertEquals(1000, locations.get(29L).pageWidth()); // page 2 size read from layout_json
	}

	@Test
	void locationOnlyRejectsFieldsOfAnotherDocumentOrNotFromAi() {
		Document other = new Document(); other.setId(99L);
		ExtractedField foreign = new ExtractedField(); foreign.setId(30L); foreign.setDocument(other); foreign.setSource("AI");
		ExtractedField manual = new ExtractedField(); manual.setId(31L); manual.setDocument(document); manual.setSource("MANUAL");
		when(extractedFields.findById(30L)).thenReturn(Optional.of(foreign));
		when(extractedFields.findById(31L)).thenReturn(Optional.of(manual));
		RegionRect rect = new RegionRect(1, 10.0, 10.0, 50.0, 50.0);
		assertThrows(ResourceNotFoundException.class, () -> service.recordInvoiceSelections(documentId,
				new RegionSelectionBatchRequest(List.of(new RegionSelectionRequest(null, rect, "", "x", 30L))), null));
		assertThrows(BadRequestException.class, () -> service.recordInvoiceSelections(documentId,
				new RegionSelectionBatchRequest(List.of(new RegionSelectionRequest(null, rect, "", "x", 31L))), null));
		assertThrows(BadRequestException.class, () -> service.recordInvoiceSelections(documentId,
				new RegionSelectionBatchRequest(List.of(new RegionSelectionRequest(null, rect, "", "x", null))), null));
		verify(selections, never()).save(any());
	}

	private RegionSelectionBatchRequest batch(String fieldName) {
		return new RegionSelectionBatchRequest(List.of(new RegionSelectionRequest(fieldName,
				new RegionRect(1, 260.0, 200.0, 200.0, 20.0), "00A1B2C3D4E5F6", " 00A1B2C3D4E5F6 ")));
	}

	private User actor(String roleCode) {
		Role role = new Role();
		role.setCode(roleCode);
		UserRoleAssignment assignment = new UserRoleAssignment();
		assignment.setRole(role);
		User user = new User();
		user.setUserRoles(Set.of(assignment));
		return user;
	}
}
