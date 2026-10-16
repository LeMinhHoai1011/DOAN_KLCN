package com.example.invoice.dto.user;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AssignRolesRequest(
		// Set semantics intentionally collapse duplicate IDs; role assignment is unordered and unique.
		@NotNull Set<Long> roleIds) {
}
