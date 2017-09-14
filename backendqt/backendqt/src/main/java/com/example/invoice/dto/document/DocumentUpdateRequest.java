package com.example.invoice.dto.document;

import com.example.invoice.entity.DocumentStatus;
import jakarta.validation.constraints.PositiveOrZero;

public record DocumentUpdateRequest(
		String originalFileName,
		String fileType,
		@PositiveOrZero Long fileSize,
		String filePath,
		DocumentStatus status) {
}
