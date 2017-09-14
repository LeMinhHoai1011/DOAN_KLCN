package com.example.invoice.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
		@Size(max = 150) String fullName,
		@Email @Size(max = 150) String email,
		@Size(max = 30) String phone,
		String avatar) {
}
