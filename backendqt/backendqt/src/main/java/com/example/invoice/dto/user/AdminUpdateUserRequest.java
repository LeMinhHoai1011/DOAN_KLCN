package com.example.invoice.dto.user;

import com.example.invoice.entity.UserRole;
import com.example.invoice.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record AdminUpdateUserRequest(
		UserRole role,
		@NotNull UserStatus status,
		Long companyId) {
}
