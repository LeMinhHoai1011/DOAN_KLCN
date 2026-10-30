package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.invoice.dto.role.*;
import com.example.invoice.entity.*;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SystemRoleProtectionTest {
	private final RoleRepository roles = mock(RoleRepository.class);
	private final PermissionRepository permissions = mock(PermissionRepository.class);
	private final RoleService service = new RoleService(roles, permissions, mock(PermissionGroupRepository.class));

	@ParameterizedTest @ValueSource(strings = {"ADMIN", "ACCOUNTANT", "EMPLOYEE", "USER"})
	void codeDisableAndDeleteAreRejectedWithoutMutation(String code) {
		Role r = role(code);
		assertThrows(BadRequestException.class, () -> service.updateRole(1L, new RoleCreateRequest("CHANGED", "changed", null, true)));
		assertThrows(BadRequestException.class, () -> service.updateRole(1L, new RoleCreateRequest(code, "changed", null, false)));
		assertThrows(BadRequestException.class, () -> service.deleteRole(1L));
		assertEquals(code, r.getCode()); assertTrue(r.isActive()); assertEquals("Original", r.getName());
		verify(roles, never()).save(any()); verify(roles, never()).delete(any());
	}

	@Test
	void userPermissionChangesAreRejectedAndIdenticalEchoPreservesAssignment() {
		Role r = role("USER"); Permission p = new Permission(); p.setId(4L); p.setCode("DOCUMENT_VIEW");
		RolePermission a = new RolePermission(); a.setRole(r); a.setPermission(p); r.getRolePermissions().add(a);
		assertThrows(BadRequestException.class, () -> service.updateRolePermissions(1L, new RoleUpdatePermissionsRequest(Set.of())));
		assertThrows(BadRequestException.class, () -> service.updateRolePermissions(1L, new RoleUpdatePermissionsRequest(Set.of(4L, 5L))));
		service.updateRolePermissions(1L, new RoleUpdatePermissionsRequest(Set.of(4L)));
		assertSame(a, r.getRolePermissions().iterator().next()); assertEquals(1, r.getRolePermissions().size());
		verifyNoInteractions(permissions); verify(roles, never()).save(any());
	}

	@Test
	void customRoleCannotBeRenamedToSystemRole() {
		Role r = role("CUSTOM");
		assertThrows(BadRequestException.class, () -> service.updateRole(1L, new RoleCreateRequest("ADMIN", "changed", null, true)));
		assertEquals("CUSTOM", r.getCode());
	}

	@Test
	void systemRoleDisplayMetadataCanStillBeEdited() {
		Role r = role("ADMIN");
		service.updateRole(1L, new RoleCreateRequest("ADMIN", "New label", "description", true));
		assertEquals("New label", r.getName()); assertEquals("ADMIN", r.getCode()); assertTrue(r.isActive());
	}

	private Role role(String code) { Role r = new Role(); r.setId(1L); r.setCode(code); r.setName("Original"); when(roles.lockById(1L)).thenReturn(Optional.of(r)); return r; }
}
