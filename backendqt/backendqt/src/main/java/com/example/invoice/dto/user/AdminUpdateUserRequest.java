package com.example.invoice.dto.user;

import com.example.invoice.entity.UserStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
		@Size(max = 50) String role,
		@NotNull UserStatus status,
		Long companyId) {
}
