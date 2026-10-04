package com.example.invoice.controller;

import com.example.invoice.dto.document.DocumentTypeResponse;
import com.example.invoice.entity.DocumentType;
import com.example.invoice.repository.DocumentTypeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/documents/types")
@RequiredArgsConstructor
public class DocumentTypeController {

	private final DocumentTypeRepository documentTypeRepository;

	@GetMapping
	@PreAuthorize("isAuthenticated()")
	public List<DocumentTypeResponse> findAll() {
		return documentTypeRepository.findAll().stream()
				.map(this::toResponse)
				.toList();
	}

	private DocumentTypeResponse toResponse(DocumentType type) {
		return new DocumentTypeResponse(
				type.getId(),
				type.getCode(),
				type.getName(),
				type.getDescription(),
				type.isActive(),
				type.getCreatedAt(),
				type.getUpdatedAt());
	}
}
