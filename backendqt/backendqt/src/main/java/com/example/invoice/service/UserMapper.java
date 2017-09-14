package com.example.invoice.service;

import com.example.invoice.dto.user.UserResponse;
import com.example.invoice.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
	public UserResponse toResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getUsername(),
				user.getFullName(),
				user.getEmail(),
				user.getPhone(),
				user.getAvatar(),
				user.getRole(),
				user.getStatus(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}
}
