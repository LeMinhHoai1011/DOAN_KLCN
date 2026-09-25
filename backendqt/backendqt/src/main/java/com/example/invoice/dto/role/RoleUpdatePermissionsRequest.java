package com.example.invoice.dto.role;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record RoleUpdatePermissionsRequest(
		@NotNull Set<Long> permissionIds) {
}
