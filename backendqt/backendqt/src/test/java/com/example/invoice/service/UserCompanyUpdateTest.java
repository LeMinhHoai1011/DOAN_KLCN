package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.invoice.dto.user.AdminUpdateUserRequest;
import com.example.invoice.entity.*;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserCompanyUpdateTest {
	@org.junit.jupiter.api.BeforeEach
	void adminGuard() { when(roles.lockAdminGuard()).thenReturn(Optional.of(new Role())); }
	private final UserRepository users = mock(UserRepository.class);
	private final RoleRepository roles = mock(RoleRepository.class);
	private final CompanyRepository companies = mock(CompanyRepository.class);
	private final InternalCompanyService internal = mock(InternalCompanyService.class);
	private final UserService service = new UserService(users, roles, companies, mock(PasswordEncoder.class), mock(UserMapper.class), internal);

	@Test
	void differentCompanyIsRejectedBeforeAnyMutation() {
		User user = userWithCompany();
		assertThrows(BadRequestException.class, () -> service.updateByAdmin(1L,
				new AdminUpdateUserRequest("ADMIN", UserStatus.INACTIVE, 43L)));
		assertEquals(42L, user.getCompany().getId()); assertEquals(UserStatus.ACTIVE, user.getStatus());
		assertEquals(0L, user.getTokenVersion());
		verifyNoInteractions(companies, internal);
		verify(roles).lockAdminGuard(); verifyNoMoreInteractions(roles);
		verify(users, never()).save(any());
	}

	@Test
	void nullAndExistingCompanyIdPreserveOwnership() {
		User user = userWithCompany();
		service.updateByAdmin(1L, new AdminUpdateUserRequest(null, UserStatus.ACTIVE, null));
		service.updateByAdmin(1L, new AdminUpdateUserRequest(null, UserStatus.ACTIVE, 42L));
		assertEquals(42L, user.getCompany().getId()); assertEquals(0L, user.getTokenVersion());
		verifyNoInteractions(companies, internal);
	}

	@Test
	void historicalNullCompanyIsNeverProvisionedByUpdate() {
		User user = new User(); when(users.findById(1L)).thenReturn(Optional.of(user));
		service.updateByAdmin(1L, new AdminUpdateUserRequest(null, UserStatus.ACTIVE, null));
		assertThrows(BadRequestException.class, () -> service.updateByAdmin(1L, new AdminUpdateUserRequest(null, UserStatus.ACTIVE, 42L)));
		assertNull(user.getCompany()); assertEquals(0L, user.getTokenVersion());
		verifyNoInteractions(companies, internal);
	}

	private User userWithCompany() {
		User user = new User(); Company company = new Company(); company.setId(42L); user.setCompany(company);
		when(users.findById(1L)).thenReturn(Optional.of(user)); return user;
	}
}
