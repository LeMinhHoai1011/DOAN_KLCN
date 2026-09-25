package com.example.invoice.dto.role;

import java.time.LocalDateTime;
import java.util.Set;

public record RoleResponse(
		Long id,
		String code,
		String name,
		String description,
		boolean active,
		Set<String> permissions,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
}
