package com.example.invoice.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank String currentPassword,
		@NotBlank
		@Size(min = 8, max = 100)
		@Pattern(regexp = ".*\\d.*", message = "New password must contain at least one number")
		@Pattern(regexp = ".*[A-Za-z].*", message = "New password must contain at least one letter")
		String newPassword) {
}
