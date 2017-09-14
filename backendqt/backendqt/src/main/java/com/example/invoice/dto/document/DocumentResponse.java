package com.example.invoice.dto.document;

import com.example.invoice.entity.DocumentStatus;
import java.time.LocalDateTime;

public record DocumentResponse(
		Long id,
		String originalFileName,
		String fileType,
		Long fileSize,
		String filePath,
		DocumentStatus status,
		Long uploadedById,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
}
