package com.example.invoice.dto.user;

import com.example.invoice.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCreateUserRequest(
		@NotBlank @Size(min = 3, max = 100) String username,
		@NotBlank @Size(min = 6, max = 100) String password,
		@NotBlank @Size(max = 150) String fullName,
		@NotBlank @Email @Size(max = 150) String email,
		@Size(max = 30) String phone,
		@NotNull UserRole role) {
}
