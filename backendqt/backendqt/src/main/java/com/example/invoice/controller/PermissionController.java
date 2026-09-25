package com.example.invoice.controller;

import com.example.invoice.dto.permission.PermissionGroupResponse;
import com.example.invoice.dto.permission.PermissionResponse;
import com.example.invoice.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

	private final RoleService roleService;

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public List<PermissionResponse> findAll() {
		return roleService.findAllPermissions();
	}

	@GetMapping("/groups")
	@PreAuthorize("hasRole('ADMIN')")
	public List<PermissionGroupResponse> findAllGroups() {
		return roleService.findAllPermissionGroups();
	}
}
