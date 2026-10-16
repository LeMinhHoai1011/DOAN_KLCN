package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.invoice.dto.user.*;
import com.example.invoice.entity.*;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminProtectionTest {
	private final UserRepository users = mock(UserRepository.class);
	private final RoleRepository roles = mock(RoleRepository.class);
	private final UserService service = new UserService(users, roles, mock(CompanyRepository.class), mock(PasswordEncoder.class), mock(UserMapper.class), mock(InternalCompanyService.class));
	private Role admin, employee;
	private User actor, target;

	@BeforeEach
	void setup() {
		admin = role(1L, "ADMIN"); employee = role(2L, "EMPLOYEE");
		actor = user(10L, "actor", admin); target = user(11L, "target", admin, employee);
		when(roles.lockAdminGuard()).thenReturn(Optional.of(admin));
		when(users.findById(11L)).thenReturn(Optional.of(target));
		when(users.findByUsername("actor")).thenReturn(Optional.of(actor));
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("actor", "unused"));
	}

	@AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

	@ParameterizedTest @ValueSource(strings = {"lock", "roleCode", "roleIds"})
	void lastActiveAdminIsProtectedAcrossAllUpdatePaths(String path) {
		when(users.countActiveAdmins()).thenReturn(1L);
		var before = Set.copyOf(target.getUserRoles());
		var error = assertThrows(BadRequestException.class, () -> change(path));
		assertTrue(error.getMessage().startsWith("LAST_ACTIVE_ADMIN_PROTECTED:"));
		assertEquals(before, target.getUserRoles()); assertEquals(UserStatus.ACTIVE, target.getStatus()); assertEquals(0L, target.getTokenVersion());
	}

	@ParameterizedTest @ValueSource(strings = {"lock", "roleCode", "roleIds"})
	void selfChangeIsRejectedEvenWithOtherAdmins(String path) {
		target.setUsername("actor");
		var error = assertThrows(BadRequestException.class, () -> change(path));
		assertTrue(error.getMessage().startsWith("ADMIN_SELF_CHANGE_FORBIDDEN:"));
		assertEquals(2, target.getUserRoles().size()); assertEquals(UserStatus.ACTIVE, target.getStatus()); assertEquals(0L, target.getTokenVersion());
		verify(users, never()).countActiveAdmins();
	}

	@Test
	void twoActiveAdminsPermitChangingOtherAdmin() {
		when(users.countActiveAdmins()).thenReturn(2L);
		change("lock"); assertEquals(UserStatus.INACTIVE, target.getStatus()); assertEquals(1L, target.getTokenVersion());
	}

	@Test
	void staleCallerAuthorityIsNotTrusted() {
		actor.setStatus(UserStatus.INACTIVE);
		assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> change("lock"));
		verify(users, never()).findById(any());
	}

	@Test
	void absentGuardFailsClosedBeforeTargetMutation() {
		when(roles.lockAdminGuard()).thenReturn(Optional.empty());
		assertThrows(BadRequestException.class, () -> change("lock"));
		verify(users, never()).findById(any());
	}

	private void change(String path) {
		if ("lock".equals(path)) service.updateByAdmin(11L, new AdminUpdateUserRequest(null, UserStatus.INACTIVE, null));
		else if ("roleCode".equals(path)) {
			when(roles.findByCode("EMPLOYEE")).thenReturn(Optional.of(employee));
			service.updateByAdmin(11L, new AdminUpdateUserRequest("EMPLOYEE", UserStatus.ACTIVE, null));
		} else {
			when(roles.findAllById(Set.of(2L))).thenReturn(List.of(employee));
			service.assignRoles(11L, new AssignRolesRequest(Set.of(2L)));
		}
	}

	private Role role(Long id, String code) { Role r = new Role(); r.setId(id); r.setCode(code); return r; }
	private User user(Long id, String name, Role... assigned) {
		User u = new User(); u.setId(id); u.setUsername(name);
		for (Role r : assigned) { var a = new UserRoleAssignment(); a.setUser(u); a.setRole(r); u.getUserRoles().add(a); }
		return u;
	}
}
