package com.example.invoice.controller;

import com.example.invoice.dto.permission.PermissionGroupResponse;
import com.example.invoice.dto.permission.PermissionResponse;
import com.example.invoice.dto.role.RoleCreateRequest;
import com.example.invoice.dto.role.RoleResponse;
import com.example.invoice.dto.role.RoleUpdatePermissionsRequest;
import com.example.invoice.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

	private final RoleService roleService;

	// ── Role CRUD (ADMIN only) ─────────────────────────────────────────────────

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<RoleResponse> findAll() {
		return roleService.findAllRoles();
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public RoleResponse findById(@PathVariable Long id) {
		return roleService.findRoleById(id);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	public RoleResponse create(@Valid @RequestBody RoleCreateRequest request) {
		return roleService.createRole(request);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	public RoleResponse update(@PathVariable Long id, @Valid @RequestBody RoleCreateRequest request) {
		return roleService.updateRole(id, request);
	}

	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		roleService.deleteRole(id);
	}

	// ── Role ↔ Permission ─────────────────────────────────────────────────────

	@GetMapping("/{roleId}/permissions")
	@PreAuthorize("hasRole('ADMIN')")
	public Set<PermissionResponse> getRolePermissions(@PathVariable Long roleId) {
		return roleService.getRolePermissions(roleId);
	}

	@PutMapping("/{roleId}/permissions")
	@PreAuthorize("hasRole('ADMIN')")
	public RoleResponse updateRolePermissions(@PathVariable Long roleId,
			@Valid @RequestBody RoleUpdatePermissionsRequest request) {
		return roleService.updateRolePermissions(roleId, request);
	}
}
