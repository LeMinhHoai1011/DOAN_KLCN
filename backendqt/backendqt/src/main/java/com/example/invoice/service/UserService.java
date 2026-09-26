package com.example.invoice.service;

import com.example.invoice.dto.user.AssignRolesRequest;
import com.example.invoice.dto.user.ChangePasswordRequest;
import com.example.invoice.dto.user.AdminCreateUserRequest;
import com.example.invoice.dto.user.AdminUpdateUserRequest;
import com.example.invoice.dto.user.UpdateUserRequest;
import com.example.invoice.dto.user.UserResponse;
import com.example.invoice.entity.Role;
import com.example.invoice.entity.User;
import com.example.invoice.entity.UserRoleAssignment;
import com.example.invoice.entity.Company;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.RoleRepository;
import com.example.invoice.repository.UserRepository;
import com.example.invoice.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final CompanyRepository companyRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserMapper userMapper;

	@Transactional(readOnly = true)
	public UserResponse currentUser(Authentication authentication) {
		return userMapper.toResponse(loadCurrent(authentication));
	}

	@Transactional(readOnly = true)
	public List<UserResponse> findAll() {
		return userRepository.findAll().stream().map(userMapper::toResponse).toList();
	}

	@Transactional
	public UserResponse createByAdmin(AdminCreateUserRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new BadRequestException("Username already exists");
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new BadRequestException("Email already exists");
		}

		User user = new User();
		user.setUsername(request.username());
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setFullName(request.fullName());
		user.setEmail(request.email());
		user.setPhone(request.phone());
		user.setRole(request.role()); // Legacy enum
		user.setCompany(loadCompany(request.companyId()));

		if (request.role() != null) {
			Role role = roleRepository.findByCode(request.role().name())
					.orElseThrow(() -> new ResourceNotFoundException("Role not found"));
			replaceRoleAssignments(user, List.of(role));
		}

		return userMapper.toResponse(userRepository.save(user));
	}

	@Transactional
	public UserResponse updateByAdmin(Long id, AdminUpdateUserRequest request) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		if (request.status() != null) user.setStatus(request.status());
		if (request.companyId() != null) user.setCompany(loadCompany(request.companyId()));

		if (request.role() != null) {
			user.setRole(request.role()); // Legacy enum
			Role role = roleRepository.findByCode(request.role().name())
					.orElseThrow(() -> new ResourceNotFoundException("Role not found"));
			boolean alreadyAssigned = user.getUserRoles().stream()
					.anyMatch(assignment -> assignment.getRole().getId().equals(role.getId()));
			if (!alreadyAssigned) {
				replaceRoleAssignments(user, List.of(role));
			}
		}

		return userMapper.toResponse(user);
	}

	@Transactional
	public UserResponse updateCurrent(Authentication authentication, UpdateUserRequest request) {
		User user = loadCurrent(authentication);
		if (request.fullName() != null) user.setFullName(request.fullName());
		if (request.email() != null) user.setEmail(request.email());
		if (request.phone() != null) user.setPhone(request.phone());
		if (request.avatar() != null) user.setAvatar(request.avatar());
		return userMapper.toResponse(user);
	}

	@Transactional
	public void changePassword(Authentication authentication, ChangePasswordRequest request) {
		User user = loadCurrent(authentication);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			throw new BadRequestException("Current password is incorrect");
		}
		user.setPassword(passwordEncoder.encode(request.newPassword()));
	}

	@Transactional
	public UserResponse assignRoles(Long userId, AssignRolesRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
		List<Role> roles = roleRepository.findAllById(request.roleIds());
		if (roles.size() != request.roleIds().size()) {
			throw new BadRequestException("One or more role IDs are invalid");
		}
		replaceRoleAssignments(user, roles);
		// Sync legacy enum with the primary role (first role in set)
		if (!roles.isEmpty()) {
			try {
				user.setRole(com.example.invoice.entity.UserRole.valueOf(roles.get(0).getCode()));
			} catch (IllegalArgumentException ignored) {
				// custom role codes not in legacy enum — leave legacy field unchanged
			}
		}
		return userMapper.toResponse(userRepository.save(user));
	}

	private void replaceRoleAssignments(User user, List<Role> roles) {
		user.getUserRoles().clear();
		for (Role role : roles) {
			UserRoleAssignment assignment = new UserRoleAssignment();
			assignment.setUser(user);
			assignment.setRole(role);
			user.getUserRoles().add(assignment);
		}
	}

	User loadCurrent(Authentication authentication) {
		return userRepository.findByUsername(authentication.getName())
				.orElseThrow(() -> new ResourceNotFoundException("User not found"));
	}

	private Company loadCompany(Long companyId) {
		if (companyId == null) {
			return null;
		}
		return companyRepository.findById(companyId)
				.orElseThrow(() -> new ResourceNotFoundException("Company not found"));
	}
}
