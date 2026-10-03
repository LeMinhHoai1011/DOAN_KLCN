package com.example.invoice.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank String currentPassword,
		@NotBlank
		@Size(min = 8, max = 100)
		@Pattern(regexp = ".*\\d.*", message = "Mật khẩu mới phải chứa ít nhất một chữ số")
		@Pattern(regexp = ".*[A-Za-z].*", message = "Mật khẩu mới phải chứa ít nhất một chữ cái")
		String newPassword) {
}
