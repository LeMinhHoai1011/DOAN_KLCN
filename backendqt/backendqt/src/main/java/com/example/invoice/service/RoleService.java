package com.example.invoice.service;

import com.example.invoice.dto.permission.PermissionGroupResponse;
import com.example.invoice.dto.permission.PermissionResponse;
import com.example.invoice.dto.role.RoleCreateRequest;
import com.example.invoice.dto.role.RoleResponse;
import com.example.invoice.dto.role.RoleUpdatePermissionsRequest;
import com.example.invoice.entity.Permission;
import com.example.invoice.entity.Role;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.exception.ResourceNotFoundException;
import com.example.invoice.repository.PermissionGroupRepository;
import com.example.invoice.repository.PermissionRepository;
import com.example.invoice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;
	private final PermissionGroupRepository permissionGroupRepository;

	// ── Role CRUD ──────────────────────────────────────────────────────────────

	public List<RoleResponse> findAllRoles() {
		return roleRepository.findAll().stream().map(this::toRoleResponse).toList();
	}

	public RoleResponse findRoleById(Long id) {
		return toRoleResponse(loadRole(id));
	}

	@Transactional
	public RoleResponse createRole(RoleCreateRequest request) {
		if (roleRepository.findByCode(request.code().toUpperCase()).isPresent()) {
			throw new BadRequestException("Role code already exists: " + request.code());
		}
		Role role = new Role();
		role.setCode(request.code().toUpperCase());
		role.setName(request.name());
		role.setDescription(request.description());
		role.setActive(request.active());
		return toRoleResponse(roleRepository.save(role));
	}

	@Transactional
	public RoleResponse updateRole(Long id, RoleCreateRequest request) {
		Role role = loadRole(id);
		// If code changed, check uniqueness
		if (!role.getCode().equalsIgnoreCase(request.code())) {
			if (roleRepository.findByCode(request.code().toUpperCase()).isPresent()) {
				throw new BadRequestException("Role code already exists: " + request.code());
			}
			role.setCode(request.code().toUpperCase());
		}
		role.setName(request.name());
		role.setDescription(request.description());
		role.setActive(request.active());
		return toRoleResponse(role);
	}

	@Transactional
	public void deleteRole(Long id) {
		Role role = loadRole(id);
		// Prevent deleting built-in system roles
		if (Set.of("ADMIN", "ACCOUNTANT", "EMPLOYEE", "USER").contains(role.getCode())) {
			throw new BadRequestException("Cannot delete built-in system role: " + role.getCode());
		}
		roleRepository.delete(role);
	}

	// ── Role Permissions ───────────────────────────────────────────────────────

	public Set<PermissionResponse> getRolePermissions(Long roleId) {
		return loadRole(roleId).getPermissions().stream()
				.map(this::toPermissionResponse)
				.collect(Collectors.toSet());
	}

	@Transactional
	public RoleResponse updateRolePermissions(Long roleId, RoleUpdatePermissionsRequest request) {
		Role role = loadRole(roleId);
		List<Permission> permissions = permissionRepository.findAllById(request.permissionIds());
		if (permissions.size() != request.permissionIds().size()) {
			throw new BadRequestException("One or more permission IDs are invalid");
		}
		role.getPermissions().clear();
		role.getPermissions().addAll(permissions);
		return toRoleResponse(roleRepository.save(role));
	}

	// ── Permission & Group ────────────────────────────────────────────────────

	public List<PermissionResponse> findAllPermissions() {
		return permissionRepository.findAll().stream().map(this::toPermissionResponse).toList();
	}

	public List<PermissionGroupResponse> findAllPermissionGroups() {
		return permissionGroupRepository.findAll().stream()
				.map(group -> new PermissionGroupResponse(
						group.getId(),
						group.getCode(),
						group.getName(),
						group.getDescription(),
						group.isActive(),
						group.getPermissions().stream().map(this::toPermissionResponse).toList()))
				.toList();
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private Role loadRole(Long id) {
		return roleRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Role not found"));
	}

	private RoleResponse toRoleResponse(Role role) {
		Set<String> permCodes = role.getPermissions().stream()
				.map(Permission::getCode).collect(Collectors.toSet());
		return new RoleResponse(
				role.getId(),
				role.getCode(),
				role.getName(),
				role.getDescription(),
				role.isActive(),
				permCodes,
				role.getCreatedAt(),
				role.getUpdatedAt());
	}

	private PermissionResponse toPermissionResponse(Permission p) {
		return new PermissionResponse(
				p.getId(),
				p.getCode(),
				p.getName(),
				p.getDescription(),
				p.isActive(),
				p.getGroup() != null ? p.getGroup().getCode() : null);
	}
}
