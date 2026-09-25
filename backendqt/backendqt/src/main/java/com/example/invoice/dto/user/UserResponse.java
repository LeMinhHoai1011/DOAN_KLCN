package com.example.invoice.dto.user;

import com.example.invoice.entity.UserRole;
import com.example.invoice.entity.UserStatus;
import java.time.LocalDateTime;
import java.util.Set;

public record UserResponse(
		Long id,
		String username,
		String fullName,
		String email,
		String phone,
		String avatar,
		UserRole role,
		Set<String> roles,
		Long companyId,
		UserStatus status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt) {
}
