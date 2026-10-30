package com.example.invoice.service;

import com.example.invoice.dto.permission.PermissionGroupResponse;
import com.example.invoice.dto.permission.PermissionResponse;
import com.example.invoice.dto.role.RoleCreateRequest;
import com.example.invoice.dto.role.RoleResponse;
import com.example.invoice.dto.role.RoleUpdatePermissionsRequest;
import com.example.invoice.entity.Permission;
import com.example.invoice.entity.Role;
import com.example.invoice.entity.RolePermission;
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
	private static final Set<String> SYSTEM_ROLES = Set.of("ADMIN", "ACCOUNTANT", "EMPLOYEE", "USER");

	private final RoleRepository roleRepository;
	private final PermissionRepository permissionRepository;
	private final PermissionGroupRepository permissionGroupRepository;

	// ── Role CRUD ──────────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<RoleResponse> findAllRoles() {
		return roleRepository.findAll().stream().map(this::toRoleResponse).toList();
	}

	@Transactional(readOnly = true)
	public RoleResponse findRoleById(Long id) {
		return toRoleResponse(loadRole(id));
	}

	@Transactional
	public RoleResponse createRole(RoleCreateRequest request) {
		if (roleRepository.findByCode(request.code().trim().toUpperCase(java.util.Locale.ROOT)).isPresent()) {
			throw new BadRequestException("Mã vai trò đã tồn tại: " + request.code());
		}
		Role role = new Role();
		role.setCode(request.code().trim().toUpperCase(java.util.Locale.ROOT));
		role.setName(request.name());
		role.setDescription(request.description());
		role.setActive(request.active());
		return toRoleResponse(roleRepository.save(role));
	}

	@Transactional
	public RoleResponse updateRole(Long id, RoleCreateRequest request) {
		Role role = loadRoleForUpdate(id);
        var expected = request.expected();
        if (expected != null && (!java.util.Objects.equals(expected.code(), role.getCode())
                || !java.util.Objects.equals(expected.name(), role.getName())
                || !java.util.Objects.equals(expected.description(), role.getDescription())
                || expected.active() != role.isActive())) throw new com.example.invoice.exception.RbacConflictException();
        String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
		if (SYSTEM_ROLES.contains(role.getCode().toUpperCase(java.util.Locale.ROOT))) {
			if (!role.getCode().equals(code) || !request.active()) throw new BadRequestException(
					"SYSTEM_ROLE_PROTECTED: Không được đổi code hoặc vô hiệu hóa role hệ thống");
		} else if (SYSTEM_ROLES.contains(code)) {
			throw new BadRequestException("SYSTEM_ROLE_PROTECTED: Không được đổi role khác thành role hệ thống");
		}
		// If code changed, check uniqueness
		if (!role.getCode().equals(code)) {
			if (roleRepository.findByCode(code).isPresent()) {
				throw new BadRequestException("Mã vai trò đã tồn tại: " + request.code());
			}
			role.setCode(code);
		}
		role.setName(request.name());
		role.setDescription(request.description());
		role.setActive(request.active());
		return toRoleResponse(role);
	}

    @Transactional
    public RoleResponse copyRole(Long sourceId, RoleCreateRequest request) {
        Role source = loadRoleForUpdate(sourceId);
        if ("USER".equals(source.getCode()))
            throw new BadRequestException("HISTORICAL_USER_PERMISSIONS_PROTECTED: Không sao chép vai trò USER lịch sử");
        RoleResponse created = createRole(request);
        Role copy = loadRole(created.id());
        for (RolePermission assignment : source.getRolePermissions()) {
            if (!assignment.getPermission().isActive()) continue;
            RolePermission link = new RolePermission();
            link.setRole(copy); link.setPermission(assignment.getPermission());
            copy.getRolePermissions().add(link);
        }
        return toRoleResponse(roleRepository.save(copy));
    }

	@Transactional
	public void deleteRole(Long id) {
		Role role = loadRoleForUpdate(id);
		// Prevent deleting built-in system roles
		if (SYSTEM_ROLES.contains(role.getCode().toUpperCase(java.util.Locale.ROOT))) {
			throw new BadRequestException("Không thể xóa vai trò hệ thống mặc định: " + role.getCode());
		}
		roleRepository.delete(role);
	}

	// ── Role Permissions ───────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public Set<PermissionResponse> getRolePermissions(Long roleId) {
		return loadRole(roleId).getRolePermissions().stream()
				.map(RolePermission::getPermission)
				.map(this::toPermissionResponse)
				.collect(Collectors.toSet());
	}

	@Transactional
	public RoleResponse updateRolePermissions(Long roleId, RoleUpdatePermissionsRequest request) {
		Role role = loadRoleForUpdate(roleId);
        Set<Long> currentIds = role.getRolePermissions().stream().map(a -> a.getPermission().getId()).collect(Collectors.toSet());
        if (request.expectedPermissionIds() != null && !currentIds.equals(request.expectedPermissionIds()))
            throw new com.example.invoice.exception.RbacConflictException();
		if ("USER".equalsIgnoreCase(role.getCode())) {
			Set<Long> current = role.getRolePermissions().stream().map(a -> a.getPermission().getId()).collect(Collectors.toSet());
			if (!current.equals(request.permissionIds())) throw new BadRequestException(
					"HISTORICAL_USER_PERMISSIONS_PROTECTED: Không được thay đổi permission USER lịch sử");
			return toRoleResponse(role);
		}
		List<Permission> permissions = permissionRepository.findAllById(request.permissionIds());
		if (permissions.size() != request.permissionIds().size()) {
			throw new BadRequestException("Một hoặc nhiều mã quyền không hợp lệ");
		}
        if (permissions.stream().anyMatch(p -> !p.isActive() && !currentIds.contains(p.getId())))
            throw new BadRequestException("PERMISSION_INACTIVE: Không được cấp mới quyền đã vô hiệu hóa");
        // Preserve retained join rows to avoid insert-before-delete unique constraint failures.
        role.getRolePermissions().removeIf(a -> !request.permissionIds().contains(a.getPermission().getId()));
        for (Permission permission : permissions) {
            if (currentIds.contains(permission.getId())) continue;
			RolePermission rolePermission = new RolePermission();
			rolePermission.setRole(role);
			rolePermission.setPermission(permission);
			role.getRolePermissions().add(rolePermission);
		}
		return toRoleResponse(roleRepository.save(role));
	}

	// ── Permission & Group ────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<PermissionResponse> findAllPermissions() {
		return permissionRepository.findAll().stream().map(this::toPermissionResponse).toList();
	}

	@Transactional(readOnly = true)
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

	private Role loadRoleForUpdate(Long id) {
        return roleRepository.lockById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò"));
    }

    private Role loadRole(Long id) {
		return roleRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò"));
	}

	private RoleResponse toRoleResponse(Role role) {
		Set<String> permCodes = role.getRolePermissions().stream()
				.map(RolePermission::getPermission)
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
