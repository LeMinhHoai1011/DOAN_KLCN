package com.example.invoice.dto.user;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record AssignRolesRequest(
		@NotNull Set<Long> roleIds) {
}
