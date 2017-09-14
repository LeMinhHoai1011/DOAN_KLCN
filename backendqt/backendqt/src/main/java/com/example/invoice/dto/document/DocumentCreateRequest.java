package com.example.invoice.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record DocumentCreateRequest(
		@NotBlank String originalFileName,
		@NotBlank String fileType,
		@NotNull @PositiveOrZero Long fileSize,
		String filePath) {
}
