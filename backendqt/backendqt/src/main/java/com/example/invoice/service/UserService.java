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
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final CompanyRepository companyRepository;
	private final PasswordEncoder passwordEncoder;
	private final UserMapper userMapper;
	private final InternalCompanyService internalCompanyService;

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
		String roleCode = request.role() == null ? "" : request.role().trim().toUpperCase(Locale.ROOT);
		if (!List.of("ADMIN", "ACCOUNTANT", "EMPLOYEE").contains(roleCode)) {
			throw new BadRequestException("USER_ROLE_NOT_ALLOWED: Chỉ được tạo tài khoản ADMIN, ACCOUNTANT hoặc EMPLOYEE");
		}
		Role role = loadActiveRole(roleCode);
		Company internalCompany = internalCompanyService.resolveRequired();
		if (userRepository.existsByUsername(request.username())) {
			throw new BadRequestException("Tên đăng nhập đã tồn tại");
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new BadRequestException("Email đã tồn tại");
		}

		User user = new User();
		user.setUsername(request.username());
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setFullName(request.fullName());
		user.setEmail(request.email());
		user.setPhone(request.phone());
		// Retain the request field for compatibility; ownership comes only from configuration.
		user.setCompany(internalCompany);
		replaceRoleAssignments(user, List.of(role));

		return userMapper.toResponse(userRepository.save(user));
	}

	@Transactional
	public UserResponse updateByAdmin(Long id, AdminUpdateUserRequest request) {
		lockAdminChanges();
		User user = userRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
		if (request.companyId() != null && (user.getCompany() == null
				|| !request.companyId().equals(user.getCompany().getId()))) {
			throw new BadRequestException("USER_COMPANY_CHANGE_NOT_ALLOWED: Không được thay đổi công ty qua API cập nhật tài khoản");
		}
		Role requestedRole = null;
		if (request.role() != null) {
			String code = request.role().trim().toUpperCase(Locale.ROOT);
			if (hasHistoricalUserRole(user)) {
				if (user.getUserRoles().size() != 1 || !"USER".equals(code)) throw historicalRoleChange();
			} else {
				requestedRole = loadActiveRole(code);
				validateAssignableRole(requestedRole);
			}
		}
		protectAdmin(user, request.status() == null ? user.getStatus() : request.status(),
				requestedRole == null ? null : List.of(requestedRole));
		if (request.status() == com.example.invoice.entity.UserStatus.INACTIVE
				&& user.getStatus() == com.example.invoice.entity.UserStatus.ACTIVE) user.revokeTokens();
		if (request.status() != null) user.setStatus(request.status());
		// Legacy clients may echo the existing ID or null; neither changes ownership.

		if (requestedRole != null) replaceRoleAssignments(user, List.of(requestedRole));

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
			throw new BadRequestException("Mật khẩu hiện tại không đúng");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
			throw new BadRequestException("Mật khẩu mới phải khác mật khẩu hiện tại");
		}
		String encoded = passwordEncoder.encode(request.newPassword());
		user.revokeTokens();
		user.setPassword(encoded);
	}

	@Transactional
	public UserResponse assignRoles(Long userId, AssignRolesRequest request) {
		lockAdminChanges();
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
		if (request.roleIds() == null || request.roleIds().isEmpty()
				|| request.roleIds().stream().anyMatch(id -> id == null || id <= 0)) {
			throw new BadRequestException("USER_ROLES_INVALID: Danh sách vai trò phải có ít nhất một ID hợp lệ");
		}
		java.util.Set<Long> existing = user.getUserRoles().stream().map(a -> a.getRole().getId())
				.collect(java.util.stream.Collectors.toSet());
		if (hasHistoricalUserRole(user)) {
			if (!existing.equals(request.roleIds())) throw historicalRoleChange();
			return userMapper.toResponse(user);
		}
		List<Role> roles = roleRepository.findAllById(request.roleIds());
		if (roles.size() != request.roleIds().size()) {
			throw new BadRequestException("Một hoặc nhiều mã vai trò không hợp lệ");
		}
		roles.forEach(this::validateAssignableRole);
		if (existing.equals(request.roleIds())) return userMapper.toResponse(user);
		protectAdmin(user, user.getStatus(), roles);
		replaceRoleAssignments(user, roles);
		return userMapper.toResponse(userRepository.save(user));
	}

	private Role loadActiveRole(String roleCode) {
		String normalized = roleCode == null ? "" : roleCode.trim().toUpperCase(Locale.ROOT);
		if (normalized.isBlank()) throw new BadRequestException("Mã vai trò không được để trống");
		Role role = roleRepository.findByCode(normalized)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò"));
		if (!role.isActive()) throw new BadRequestException("Vai trò đã bị vô hiệu hóa");
		return role;
	}

	private void replaceRoleAssignments(User user, List<Role> roles) {
		java.util.Set<Long> requested = roles.stream().map(Role::getId).collect(java.util.stream.Collectors.toSet());
		user.getUserRoles().removeIf(a -> !requested.contains(a.getRole().getId()));
		for (Role role : roles) {
			if (user.getUserRoles().stream().anyMatch(a -> a.getRole().getId().equals(role.getId()))) continue;
			UserRoleAssignment assignment = new UserRoleAssignment();
			assignment.setUser(user);
			assignment.setRole(role);
			user.getUserRoles().add(assignment);
		}
	}

	private boolean hasHistoricalUserRole(User user) {
		return user.getUserRoles().stream().anyMatch(a -> "USER".equals(a.getRole().getCode()));
	}

	/** All status/assignment writers serialize here before reading target state or counting admins. */
	private void lockAdminChanges() {
		Role guard = roleRepository.lockAdminGuard().orElseThrow(() -> new BadRequestException(
				"ADMIN_GUARD_MISSING: Chưa có role ADMIN để bảo vệ thao tác quản trị"));
		if (!guard.isActive()) throw new BadRequestException("ADMIN_GUARD_INACTIVE: Role ADMIN không hoạt động");
		Authentication caller = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (caller != null) {
			User actor = userRepository.findByUsername(caller.getName()).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Caller not found"));
			if (actor.getStatus() != com.example.invoice.entity.UserStatus.ACTIVE || !hasActiveAdminRole(actor)) {
				throw new org.springframework.security.access.AccessDeniedException("Caller is no longer an active administrator");
			}
		}
	}

	private boolean hasActiveAdminRole(User user) {
		return user.getUserRoles().stream().anyMatch(a -> "ADMIN".equals(a.getRole().getCode()) && a.getRole().isActive());
	}

	private void protectAdmin(User target, com.example.invoice.entity.UserStatus nextStatus, List<Role> nextRoles) {
		boolean admin = hasActiveAdminRole(target);
		if (!admin) return;
		boolean removesAdmin = nextRoles != null && nextRoles.stream().noneMatch(r -> "ADMIN".equals(r.getCode()) && r.isActive());
		boolean locks = nextStatus == com.example.invoice.entity.UserStatus.INACTIVE;
		if (!locks && !removesAdmin) return;
		Authentication caller = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (caller == null) throw new org.springframework.security.access.AccessDeniedException("An administrator identity is required");
		if (target.getUsername().equals(caller.getName())) throw new BadRequestException(
				"ADMIN_SELF_CHANGE_FORBIDDEN: Không được tự khóa hoặc tự bỏ quyền ADMIN");
		if (target.getStatus() == com.example.invoice.entity.UserStatus.ACTIVE && userRepository.countActiveAdmins() <= 1) {
			throw new BadRequestException("LAST_ACTIVE_ADMIN_PROTECTED: Không được vô hiệu hóa ADMIN hoạt động cuối cùng");
		}
	}

	private BadRequestException historicalRoleChange() {
		return new BadRequestException("HISTORICAL_USER_ROLES_PROTECTED: Chỉ được giữ nguyên danh sách vai trò của tài khoản có USER lịch sử");
	}

	private void validateAssignableRole(Role role) {
		if ("USER".equals(role.getCode())) throw new BadRequestException("USER_ROLE_NOT_ALLOWED: Không được gán mới vai trò USER");
		if (!role.isActive()) throw new BadRequestException("USER_ROLE_INACTIVE: Không được gán vai trò đã bị vô hiệu hóa");
	}

	public User loadCurrent(Authentication authentication) {
		return userRepository.findByUsername(authentication.getName())
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
	}

	private Company loadCompany(Long companyId) {
		if (companyId == null) {
			return null;
		}
		return companyRepository.findById(companyId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy công ty"));
	}
}
