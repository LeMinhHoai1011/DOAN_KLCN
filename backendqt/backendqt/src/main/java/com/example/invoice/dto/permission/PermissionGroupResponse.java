package com.example.invoice.dto.permission;

import java.util.List;

public record PermissionGroupResponse(
		Long id,
		String code,
		String name,
		String description,
		boolean active,
		List<PermissionResponse> permissions) {
}
