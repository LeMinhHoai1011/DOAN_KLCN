package com.example.invoice.controller;

import com.example.invoice.dto.document.DocumentCreateRequest;
import com.example.invoice.dto.document.DocumentResponse;
import com.example.invoice.dto.document.DocumentUpdateRequest;
import com.example.invoice.dto.document.OCRResultRequest;
import com.example.invoice.dto.document.OCRResultResponse;
import com.example.invoice.dto.invoice.InvoiceResponse;
import com.example.invoice.service.DocumentService;
import com.example.invoice.service.InvoiceService;
import com.example.invoice.service.OCRResultService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
	private final OCRResultService ocrResultService;

	@PostMapping
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_CREATE') or hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentResponse create(@Valid @RequestBody DocumentCreateRequest request, Authentication authentication) {
		return documentService.create(request, authentication);
	}

	@PostMapping("/upload")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_CREATE') or hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentResponse upload(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "typeId", required = false) Long typeId,
			Authentication authentication) {
		return documentService.createFromUpload(file, typeId, authentication);
	}

	@PostMapping("/{id}/versions")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasRole('ADMIN')")
	public DocumentResponse uploadNewVersion(
			@PathVariable Long id,
			@RequestParam("file") MultipartFile file,
			Authentication authentication) {
		return documentService.uploadNewVersion(id, file, authentication);
	}

	@GetMapping
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_VIEW') or hasRole('ADMIN')")
	public List<DocumentResponse> findAll() {
		return documentService.findAll();
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
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(doc.fileType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.originalFileName() + "\"")
				.body(resource);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasRole('ADMIN')")
	public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody DocumentUpdateRequest request) {
		return documentService.update(id, request);
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

	@PutMapping("/{id}/ocr")
	@PreAuthorize("hasAuthority('PERMISSION_DOCUMENT_UPDATE') or hasRole('ADMIN')")
	public OCRResultResponse upsertOcr(@PathVariable Long id, @Valid @RequestBody OCRResultRequest request) {
		return ocrResultService.upsert(id, request);
	}
}
