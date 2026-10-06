package com.example.invoice.controller;

import com.example.invoice.dto.document.DocumentTypeResponse;
import com.example.invoice.dto.document.DocumentTypeRequest;
import com.example.invoice.service.DocumentTypeService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents/types")
@RequiredArgsConstructor
public class DocumentTypeController {

	private final DocumentTypeService documentTypeService;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public List<DocumentTypeResponse> findAll() {
		return documentTypeService.findAll();
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	public DocumentTypeResponse create(@Valid @RequestBody DocumentTypeRequest request) {
		return documentTypeService.create(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public DocumentTypeResponse update(@PathVariable Long id, @Valid @RequestBody DocumentTypeRequest request) {
		return documentTypeService.update(id, request);
	}
}
