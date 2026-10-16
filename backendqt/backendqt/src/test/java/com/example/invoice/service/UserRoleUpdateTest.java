package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.invoice.dto.user.*;
import com.example.invoice.entity.*;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserRoleUpdateTest {
	@org.junit.jupiter.api.BeforeEach
	void adminGuard() { when(roles.lockAdminGuard()).thenReturn(Optional.of(new Role())); }
	private final UserRepository users = mock(UserRepository.class);
	private final RoleRepository roles = mock(RoleRepository.class);
	private final UserService service = new UserService(users, roles, mock(CompanyRepository.class),
			mock(PasswordEncoder.class), mock(UserMapper.class), mock(InternalCompanyService.class));

	@ParameterizedTest
	@ValueSource(strings = {"USER", "INACTIVE", "EMPTY", "MISSING", "NULL_ID"})
	void invalidListsNeverChangeExistingAssignmentOrTokenVersion(String kind) {
		User user = user(role(1, "EMPLOYEE", true));
		var original = user.getUserRoles().iterator().next();
		Set<Long> ids = Set.of(2L);
		if ("EMPTY".equals(kind)) ids = Set.of();
		else if ("NULL_ID".equals(kind)) { ids = new HashSet<>(); ids.add(null); }
		else when(roles.findAllById(ids)).thenReturn("MISSING".equals(kind) ? List.of()
				: List.of(role(2, "USER".equals(kind) ? "USER" : "ACCOUNTANT", !"INACTIVE".equals(kind))));
		Set<Long> requested = ids;
		assertThrows(BadRequestException.class, () -> service.assignRoles(42L, new AssignRolesRequest(requested)));
		assertSame(original, user.getUserRoles().iterator().next()); assertEquals(1, user.getUserRoles().size());
		assertEquals(0L, user.getTokenVersion()); verify(users, never()).save(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {"USER", "ACCOUNTANT"})
	void invalidCodeIsCheckedBeforeStatusMutation(String code) {
		User user = user(role(1, "EMPLOYEE", true));
		when(roles.findByCode(code)).thenReturn(Optional.of(role(2, code, "USER".equals(code))));
		assertThrows(BadRequestException.class, () -> service.updateByAdmin(42L, new AdminUpdateUserRequest(code, UserStatus.INACTIVE, null)));
		assertEquals(UserStatus.ACTIVE, user.getStatus()); assertEquals(0L, user.getTokenVersion());
		assertEquals("EMPLOYEE", user.getUserRoles().iterator().next().getRole().getCode());
	}

	@Test
	void historicalUserOnlyAcceptsIdenticalListEvenWhenInactive() {
		User user = user(role(1, "USER", false), role(2, "EMPLOYEE", true));
		var before = Set.copyOf(user.getUserRoles());
		service.assignRoles(42L, new AssignRolesRequest(Set.of(1L, 2L)));
		assertThrows(BadRequestException.class, () -> service.assignRoles(42L, new AssignRolesRequest(Set.of(1L))));
		assertThrows(BadRequestException.class, () -> service.assignRoles(42L, new AssignRolesRequest(Set.of(2L))));
		assertThrows(BadRequestException.class, () -> service.assignRoles(42L, new AssignRolesRequest(Set.of(1L, 2L, 3L))));
		assertThrows(BadRequestException.class, () -> service.updateByAdmin(42L, new AdminUpdateUserRequest("EMPLOYEE", UserStatus.INACTIVE, null)));
		assertEquals(before, user.getUserRoles()); assertEquals(UserStatus.ACTIVE, user.getStatus());
		assertEquals(0L, user.getTokenVersion()); verify(roles, atLeastOnce()).lockAdminGuard(); verifyNoMoreInteractions(roles); verify(users, never()).save(any());
	}

	@Test
	void singleHistoricalUserRoleCanBeEchoedWithoutReplacingAssignment() {
		User user = user(role(1, "USER", true)); var original = user.getUserRoles().iterator().next();
		service.updateByAdmin(42L, new AdminUpdateUserRequest(" user ", UserStatus.ACTIVE, null));
		assertSame(original, user.getUserRoles().iterator().next()); assertEquals(0L, user.getTokenVersion());
		verify(roles).lockAdminGuard(); verifyNoMoreInteractions(roles);
	}

	@Test
	void validRoleReplacementPreservesRetainedAssignmentsAndTokenVersion() {
		Role employee = role(1, "EMPLOYEE", true), accountant = role(2, "ACCOUNTANT", true);
		User user = user(employee); var original = user.getUserRoles().iterator().next();
		when(roles.findAllById(Set.of(1L, 2L))).thenReturn(List.of(employee, accountant));
		service.assignRoles(42L, new AssignRolesRequest(Set.of(1L, 2L)));
		assertTrue(user.getUserRoles().contains(original)); assertEquals(2, user.getUserRoles().size());
		assertEquals(0L, user.getTokenVersion());
	}

	private User user(Role... assigned) {
		User user = new User(); user.setId(42L);
		for (Role role : assigned) { var assignment = new UserRoleAssignment(); assignment.setUser(user); assignment.setRole(role); user.getUserRoles().add(assignment); }
		when(users.findById(42L)).thenReturn(Optional.of(user)); return user;
	}

	private Role role(long id, String code, boolean active) {
		Role role = new Role(); role.setId(id); role.setCode(code); role.setActive(active); return role;
	}
}
