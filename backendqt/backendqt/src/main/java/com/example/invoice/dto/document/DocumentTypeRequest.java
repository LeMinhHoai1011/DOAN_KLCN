package com.example.invoice.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record DocumentTypeRequest(
		@NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "Mã chỉ gồm chữ in hoa, số và dấu gạch dưới") String code,
		@NotBlank @Size(max = 100) String name,
		@Size(max = 255) String description,
		boolean active) {
}
