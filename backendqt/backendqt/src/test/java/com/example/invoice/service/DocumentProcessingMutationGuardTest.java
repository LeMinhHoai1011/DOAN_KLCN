package com.example.invoice.service;

import com.example.invoice.dto.document.DocumentUpdateRequest;
import com.example.invoice.entity.*;
import com.example.invoice.exception.PipelineException;
import com.example.invoice.repository.*;
import io.minio.MinioClient;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentProcessingMutationGuardTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @ParameterizedTest @ValueSource(booleans = {true,false})
    void metadataAndNewVersionCannotChangeInputWhileDocumentOrJobIsRunning(boolean processingLabel) {
        var documents = mock(DocumentRepository.class); var jobs = mock(ProcessingJobRepository.class);
        var types = mock(DocumentTypeRepository.class); var versions = mock(DocumentVersionRepository.class);
        var classifications = mock(ClassificationRepository.class); var users = mock(UserService.class);
        var storage = mock(StorageService.class); var minio = mock(MinioClient.class); var validation = mock(FileValidationService.class);
        var role = new Role(); role.setCode("ADMIN"); var assignment = new UserRoleAssignment(); assignment.setRole(role);
        var actor = new User(); actor.setUserRoles(Set.of(assignment));
        var authentication = new TestingAuthenticationToken("admin", "unused"); SecurityContextHolder.getContext().setAuthentication(authentication);
        when(users.loadCurrent(any())).thenReturn(actor);
        var document = new Document(); document.setId(1L); document.setOriginalFileName("old.png"); document.setFilePath("original-object");
        document.setStatus(processingLabel ? DocumentStatus.PROCESSING : DocumentStatus.UPLOADED);
        when(documents.findById(1L)).thenReturn(Optional.of(document)); when(documents.findByIdForUpdate(1L)).thenReturn(Optional.of(document));
        when(jobs.existsByDocumentIdAndJobTypeAndStatus(1L, "OCR_AI_PIPELINE", "RUNNING")).thenReturn(true);
        var service = new DocumentService(documents, types, versions, classifications, users, storage, minio, validation);
        ReflectionTestUtils.setField(service, "activeProcessingJobs", jobs);
        ReflectionTestUtils.setField(service, "mutationEntityManager", mock(jakarta.persistence.EntityManager.class));
        var error = assertThrows(PipelineException.class, () -> service.update(1L,
                new DocumentUpdateRequest("new.png", null, null, "different-object", DocumentStatus.UPLOADED, null), authentication));
        assertEquals("PROCESSING_IN_PROGRESS", error.failure().errorCode()); assertEquals(409, error.httpStatus());
        assertThrows(PipelineException.class, () -> service.uploadNewVersion(1L,
                new MockMultipartFile("file", "new.png", "image/png", new byte[] {1}), authentication));
        assertEquals("old.png", document.getOriginalFileName()); assertEquals("original-object", document.getFilePath());
        verifyNoInteractions(minio, storage, versions);
    }

    @ParameterizedTest @ValueSource(strings={"APPROVED","COMPLETED","STATUS_BYPASS"})
    void lockedDocumentsAndClientStatusCannotBeMutated(String scenario) {
        var documents=mock(DocumentRepository.class);var versions=mock(DocumentVersionRepository.class);var users=mock(UserService.class);
        var storage=mock(StorageService.class);var minio=mock(MinioClient.class);
        var role=new Role();role.setCode("ADMIN");var assignment=new UserRoleAssignment();assignment.setRole(role);var actor=new User();actor.setUserRoles(Set.of(assignment));when(users.loadCurrent(any())).thenReturn(actor);
        var d=new Document();d.setId(1L);d.setOriginalFileName("original.png");d.setStatus(scenario.equals("COMPLETED")?DocumentStatus.COMPLETED:DocumentStatus.PROCESSED);if(scenario.equals("APPROVED"))d.setReviewStatus(ReviewStatus.APPROVED);
        when(documents.findById(1L)).thenReturn(Optional.of(d));when(documents.findByIdForUpdate(1L)).thenReturn(Optional.of(d));
        var service=new DocumentService(documents,mock(DocumentTypeRepository.class),versions,mock(ClassificationRepository.class),users,storage,minio,mock(FileValidationService.class));
        assertThrows(com.example.invoice.exception.BadRequestException.class,()->service.update(1L,new DocumentUpdateRequest("changed.png",null,null,null,DocumentStatus.UPLOADED,null),null));
        assertEquals("original.png",d.getOriginalFileName());
        if(!scenario.equals("STATUS_BYPASS")) {
          assertThrows(com.example.invoice.exception.BadRequestException.class,()->service.uploadNewVersion(1L,new MockMultipartFile("file","new.png","image/png",new byte[]{1}),null));
          assertThrows(com.example.invoice.exception.BadRequestException.class,()->service.delete(1L));
        }
        verifyNoInteractions(versions,storage,minio);
    }
}
