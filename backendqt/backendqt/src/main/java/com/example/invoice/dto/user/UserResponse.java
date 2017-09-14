package com.example.invoice.dto.user;

import com.example.invoice.entity.UserRole;
import com.example.invoice.entity.UserStatus;
import java.time.LocalDateTime;

public record UserResponse(
		Long id,
		String username,
		String fullName,
		String email,
		String phone,
		String avatar,
		UserRole role,
		UserStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
}
