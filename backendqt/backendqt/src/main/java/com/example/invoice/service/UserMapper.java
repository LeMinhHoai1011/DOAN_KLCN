package com.example.invoice.service;

import com.example.invoice.dto.user.UserResponse;
import com.example.invoice.entity.User;
import com.example.invoice.entity.Role;
import com.example.invoice.entity.UserRole;
import com.example.invoice.entity.UserRoleAssignment;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {
	public UserResponse toResponse(User user) {
		Set<String> roles = user.getUserRoles().stream()
				.map(UserRoleAssignment::getRole)
				.filter(Role::isActive)
				.map(Role::getCode)
				.collect(Collectors.toUnmodifiableSet());
		UserRole primaryRole = List.of(UserRole.ADMIN, UserRole.ACCOUNTANT, UserRole.EMPLOYEE, UserRole.USER).stream()
				.filter(role -> roles.contains(role.name()))
				.findFirst()
				.orElse(null);
		return new UserResponse(
				user.getId(),
				user.getUsername(),
				user.getFullName(),
				user.getEmail(),
				user.getPhone(),
				user.getAvatar(),
				primaryRole,
				roles,
				user.getCompany() == null ? null : user.getCompany().getId(),
				user.getStatus(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}
}
