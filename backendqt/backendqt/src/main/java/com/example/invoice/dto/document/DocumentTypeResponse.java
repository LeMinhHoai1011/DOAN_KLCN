package com.example.invoice.dto.document;

import java.time.LocalDateTime;

public record DocumentTypeResponse(
        Long id,
        String code,
        String name,
        String description,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
