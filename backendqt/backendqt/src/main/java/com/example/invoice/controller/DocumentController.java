package com.example.invoice.controller;

import com.example.invoice.dto.document.DocumentCreateRequest;
import com.example.invoice.dto.document.DocumentResponse;
import com.example.invoice.dto.document.DocumentUpdateRequest;
import com.example.invoice.dto.document.DocumentUploadResponse;
import com.example.invoice.dto.document.OCRResultRequest;
import com.example.invoice.dto.document.OCRResultResponse;
import com.example.invoice.dto.ai.AiDocumentProcessingResponse;
import com.example.invoice.service.DocumentAiProcessingService;
import com.example.invoice.service.DocumentUploadProcessingService;
import com.example.invoice.dto.invoice.InvoiceResponse;
import com.example.invoice.dto.invoice.ExtractedFieldResponse;
import com.example.invoice.dto.invoice.ExtractedFieldCorrectionRequest;
import com.example.invoice.service.DocumentService;
import com.example.invoice.service.ExtractedFieldCorrectionService;
import com.example.invoice.service.InvoiceService;
import com.example.invoice.service.OCRResultService;
import jakarta.validation.Valid;
import java.util.List;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {
	private final DocumentService documentService;
	private final InvoiceService invoiceService;
	private final ExtractedFieldCorrectionService extractedFieldCorrectionService;
	private final OCRResultService ocrResultService;
	private final DocumentAiProcessingService documentAiProcessingService;
	private final DocumentUploadProcessingService documentUploadProcessingService;
	private final com.example.invoice.service.ProcessingLogService processingLogService;
    private final com.example.invoice.service.PipelineExecutionService pipelineExecutionService;

    @GetMapping("/{id}/processing-history")
    @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
    public com.example.invoice.dto.document.PipelineHistory processingHistory(@PathVariable Long id) { return pipelineExecutionService.history(id); }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_CREATE') or hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasRole('ADMIN')")
    public AiDocumentProcessingResponse retry(@PathVariable Long id) { return documentAiProcessingService.retry(id); }

	@GetMapping("/{id}/assessment")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
	public com.example.invoice.dto.document.DocumentAssessment assessment(@PathVariable Long id) {
		return processingLogService.assessment(id);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_CREATE') or hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentResponse create(@Valid @RequestBody DocumentCreateRequest request, Authentication authentication) {
		return documentService.create(request, authentication);
	}

	@PostMapping("/upload")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_CREATE') or hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentUploadResponse upload(
			@RequestParam("file") MultipartFile file,
			Authentication authentication) {
		return documentUploadProcessingService.uploadAndProcess(file, authentication);
	}

	@PostMapping("/{id}/versions")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasRole('ADMIN')")
	public DocumentResponse uploadNewVersion(
			@PathVariable Long id,
			@RequestParam("file") MultipartFile file,
			Authentication authentication) {
		return documentService.uploadNewVersion(id, file, authentication);
	}

	@PostMapping("/{id}/process")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_CREATE') or hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasRole('ADMIN')")
	public AiDocumentProcessingResponse process(@PathVariable Long id) {
		return documentAiProcessingService.process(id, false);
	}

	@PostMapping("/{id}/reprocess")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_CREATE') or hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasRole('ADMIN')")
	public AiDocumentProcessingResponse reprocess(@PathVariable Long id) {
		return documentAiProcessingService.process(id, true);
	}

	@GetMapping
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
	public Page<DocumentResponse> findAll(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
			@RequestParam(required = false) Long companyId, @RequestParam(required = false) com.example.invoice.entity.DocumentStatus processingStatus,
			@RequestParam(required = false) com.example.invoice.entity.ReviewStatus reviewStatus, @RequestParam(required = false) Long typeId,
			@RequestParam(required = false) Long uploaderId, @RequestParam(required = false) String search,
			Pageable pageable, Authentication authentication) {
		return documentService.findPage(dateFrom, dateTo, companyId, processingStatus, reviewStatus, typeId, uploaderId, search, pageable, authentication);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
	public DocumentResponse findById(@PathVariable Long id) {
		return documentService.findById(id);
	}
	
	@GetMapping("/{id}/download")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_DOWNLOAD') or hasRole('ADMIN')")
	public ResponseEntity<Resource> download(@PathVariable Long id) {
		DocumentResponse doc = documentService.findById(id);
		Resource resource = documentService.download(id);
        return DocumentArchiveController.file(resource,doc.fileType(),doc.originalFileName(),false);
	}

	@GetMapping("/{id}/preview")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
	public ResponseEntity<Resource> preview(@PathVariable Long id) {
		DocumentResponse doc = documentService.findById(id);
		Resource resource = documentService.download(id);
        return DocumentArchiveController.file(resource,doc.fileType(),doc.originalFileName(),true);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasRole('ADMIN')")
	public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody DocumentUpdateRequest request, Authentication authentication) {
		return documentService.update(id, request, authentication);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_DELETE') or hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		documentService.delete(id);
	}

	@GetMapping("/{id}/ocr")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
	public OCRResultResponse getOcr(@PathVariable Long id) {
		return ocrResultService.findByDocumentId(id);
	}

	@GetMapping("/{id}/invoice")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
	public InvoiceResponse getInvoice(@PathVariable Long id) {
		return invoiceService.findByDocumentId(id);
	}

	@GetMapping("/{id}/extracted-fields")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
	public List<ExtractedFieldResponse> getExtractedFields(@PathVariable Long id) {
		return invoiceService.findExtractedFieldsByDocumentId(id);
	}

	@PatchMapping("/{id}/extracted-fields/{fieldId}/correction")
	@PreAuthorize("hasRole('ADMIN') or hasRole('ACCOUNTANT')")
	public ExtractedFieldResponse correctExtractedField(@PathVariable Long id, @PathVariable Long fieldId,
			@Valid @RequestBody ExtractedFieldCorrectionRequest request, Authentication authentication) {
		extractedFieldCorrectionService.correct(id, fieldId, request, authentication);
		return invoiceService.findExtractedFieldsByDocumentId(id).stream()
				.filter(field -> field.id().equals(fieldId))
				.findFirst()
				.orElseThrow(() -> new com.example.invoice.exception.ResourceNotFoundException("Không tìm thấy trường trích xuất"));
	}

	@PutMapping("/{id}/ocr")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasRole('ADMIN')")
	public OCRResultResponse upsertOcr(@PathVariable Long id, @Valid @RequestBody OCRResultRequest request) {
		return ocrResultService.upsert(id, request);
	}
}
