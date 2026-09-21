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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {
	private final DocumentService documentService;
	private final InvoiceService invoiceService;
	private final OCRResultService ocrResultService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentResponse create(@Valid @RequestBody DocumentCreateRequest request, Authentication authentication) {
		return documentService.create(request, authentication);
	}

	@PostMapping("/upload")
	@ResponseStatus(HttpStatus.CREATED)
	public DocumentResponse upload(@ModelAttribute("file") MultipartFile file, Authentication authentication) {
		return documentService.createFromUpload(file, authentication);
	}

	@GetMapping
	public List<DocumentResponse> findAll() {
		return documentService.findAll();
	}

	@GetMapping("/{id}")
	public DocumentResponse findById(@PathVariable Long id) {
		return documentService.findById(id);
	}

	@PutMapping("/{id}")
	public DocumentResponse update(@PathVariable Long id, @Valid @RequestBody DocumentUpdateRequest request) {
		return documentService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		documentService.delete(id);
	}

	@GetMapping("/{id}/ocr")
	public OCRResultResponse getOcr(@PathVariable Long id) {
		return ocrResultService.findByDocumentId(id);
	}

	@GetMapping("/{id}/invoice")
	public InvoiceResponse getInvoice(@PathVariable Long id) {
		return invoiceService.findByDocumentId(id);
	}

	@PutMapping("/{id}/ocr")
	public OCRResultResponse upsertOcr(@PathVariable Long id, @Valid @RequestBody OCRResultRequest request) {
		return ocrResultService.upsert(id, request);
	}
}
