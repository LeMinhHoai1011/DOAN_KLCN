package com.example.invoice.dto.permission;

public record PermissionResponse(
		Long id,
		String code,
		String name,
		String description,
		boolean active,
		String groupCode) {
}
