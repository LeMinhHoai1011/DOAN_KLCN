package com.example.invoice.dto.document;

import java.time.LocalDateTime;

public record DocumentTypeResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active,
        long documentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
	public DocumentTypeResponse(Long id, String code, String name, String description, boolean active,
			LocalDateTime createdAt, LocalDateTime updatedAt) {
		this(id, code, name, description, active, 0, createdAt, updatedAt);
	}
}
