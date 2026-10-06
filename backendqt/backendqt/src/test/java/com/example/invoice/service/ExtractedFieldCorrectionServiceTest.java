package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.invoice.dto.invoice.ExtractedFieldCorrectionRequest;
import com.example.invoice.entity.Document;
import com.example.invoice.entity.DocumentStatus;
import com.example.invoice.entity.ExtractedField;
import com.example.invoice.entity.FieldCorrection;
import com.example.invoice.entity.ReviewStatus;
import com.example.invoice.entity.Role;
import com.example.invoice.entity.User;
import com.example.invoice.entity.UserRoleAssignment;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.ExtractedFieldRepository;
import com.example.invoice.repository.FieldCorrectionRepository;
import java.math.BigDecimal;
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
class ExtractedFieldCorrectionServiceTest {
	@Mock DocumentService documents;
	@Mock UserService users;
	@Mock ExtractedFieldRepository fields;
	@Mock FieldCorrectionRepository corrections;

	private final Long documentId = 17L;
	private final Long fieldId = 29L;
	private Document document;
	private ExtractedField field;
	private User accountant;
	private ExtractedFieldCorrectionService service;

	@BeforeEach
	void setUp() {
		document = new Document();
		document.setId(documentId);
		document.setStatus(DocumentStatus.PROCESSED);
		document.setReviewStatus(ReviewStatus.PENDING);
		field = new ExtractedField();
		field.setId(fieldId);
		field.setDocument(document);
		field.setFieldName("deliveryAddress");
		field.setFieldValue("Duong Nguyen Van Linh");
		field.setSource("AI");
		field.setConfidence(new BigDecimal("0.93"));
		accountant = actor("ACCOUNTANT");
		service = new ExtractedFieldCorrectionService(documents, users, fields, corrections);
		lenient().when(users.loadCurrent(null)).thenReturn(accountant);
		lenient().when(documents.load(documentId)).thenReturn(document);
		lenient().when(fields.findById(fieldId)).thenReturn(Optional.of(field));
		lenient().when(corrections.save(any(FieldCorrection.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void accountantCanCorrectCompanyScopedProcessedFieldWithoutChangingAiData() {
		service.correct(documentId, fieldId, new ExtractedFieldCorrectionRequest("Đường Nguyễn Văn Linh"), null);

		ArgumentCaptor<FieldCorrection> captor = ArgumentCaptor.forClass(FieldCorrection.class);
		verify(corrections).save(captor.capture());
		FieldCorrection saved = captor.getValue();
		assertEquals(field, saved.getField());
		assertEquals(accountant, saved.getCorrectedBy());
		assertEquals("Duong Nguyen Van Linh", saved.getOldValue());
		assertEquals("Đường Nguyễn Văn Linh", saved.getNewValue());
		assertEquals("Duong Nguyen Van Linh", field.getFieldValue());
		assertEquals(new BigDecimal("0.93"), field.getConfidence());
	}

	@Test
	void accountantCanCorrectNeedReviewAndCorrectionHistoryChainsFromEffectiveValue() {
		document.setStatus(DocumentStatus.NEED_REVIEW);
		FieldCorrection prior = new FieldCorrection();
		prior.setNewValue("Đường Nguyễn Văn Linh");
		when(corrections.findFirstByFieldIdOrderByCreatedAtDescIdDesc(fieldId)).thenReturn(Optional.of(prior));

		service.correct(documentId, fieldId, new ExtractedFieldCorrectionRequest("Đường Nguyễn Văn Linh, Quận 7"), null);

		ArgumentCaptor<FieldCorrection> captor = ArgumentCaptor.forClass(FieldCorrection.class);
		verify(corrections).save(captor.capture());
		assertEquals("Đường Nguyễn Văn Linh", captor.getValue().getOldValue());
		assertEquals("Duong Nguyen Van Linh", field.getFieldValue());
	}

	@Test
	void accountantFromAnotherCompanyIsDeniedByDocumentServiceScope() {
		when(documents.load(documentId)).thenThrow(new ResourceNotFoundException("Không tìm thấy chứng từ"));

		assertThrows(ResourceNotFoundException.class, () -> service.correct(documentId, fieldId,
				new ExtractedFieldCorrectionRequest("Không được phép"), null));

		verify(corrections, never()).save(any());
	}

	@Test
	void employeeCannotCorrectEvenAnOwnedDocument() {
		when(users.loadCurrent(null)).thenReturn(actor("EMPLOYEE"));

		assertThrows(AccessDeniedException.class, () -> service.correct(documentId, fieldId,
				new ExtractedFieldCorrectionRequest("Không được phép"), null));

		verify(documents, never()).load(documentId);
		verify(corrections, never()).save(any());
	}

	@Test
	void completedOrApprovedDocumentsAreReadOnly() {
		document.setStatus(DocumentStatus.COMPLETED);
		assertThrows(BadRequestException.class, () -> service.correct(documentId, fieldId,
				new ExtractedFieldCorrectionRequest("Không được phép"), null));
		document.setStatus(DocumentStatus.PROCESSED);
		document.setReviewStatus(ReviewStatus.APPROVED);
		assertThrows(BadRequestException.class, () -> service.correct(documentId, fieldId,
				new ExtractedFieldCorrectionRequest("Không được phép"), null));
		verify(corrections, never()).save(any());
	}

	@Test
	void rejectsFieldsNotOwnedByDocumentAndInvoiceCoreFields() {
		ExtractedField otherDocumentField = new ExtractedField();
		Document otherDocument = new Document();
		otherDocument.setId(documentId + 1);
		otherDocumentField.setDocument(otherDocument);
		when(fields.findById(fieldId)).thenReturn(Optional.of(otherDocumentField));
		assertThrows(ResourceNotFoundException.class, () -> service.correct(documentId, fieldId,
				new ExtractedFieldCorrectionRequest("wrong document"), null));
		when(fields.findById(fieldId)).thenReturn(Optional.of(field));
		field.setFieldName("sellerName");
		assertThrows(BadRequestException.class, () -> service.correct(documentId, fieldId,
				new ExtractedFieldCorrectionRequest("Invoice data uses invoice update"), null));
		verify(corrections, never()).save(any());
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
