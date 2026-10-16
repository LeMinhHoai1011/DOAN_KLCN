package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.invoice.config.InternalCompanyProperties;
import com.example.invoice.dto.user.AdminCreateUserRequest;
import com.example.invoice.entity.Role;
import com.example.invoice.exception.BadRequestException;
import com.example.invoice.repository.CompanyRepository;
import com.example.invoice.repository.RoleRepository;
import com.example.invoice.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminUserCreationTest {
	private final UserRepository users = mock(UserRepository.class);
	private final RoleRepository roles = mock(RoleRepository.class);
	private final CompanyRepository companies = mock(CompanyRepository.class);
	private final PasswordEncoder encoder = mock(PasswordEncoder.class);
	private final UserMapper mapper = mock(UserMapper.class);
	private final InternalCompanyProperties properties = new InternalCompanyProperties();
	private UserService service;

	@BeforeEach
	void setUp() {
		service = new UserService(users, roles, companies, encoder, mapper,
				new InternalCompanyService(properties, companies));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"USER", " user ", "CUSTOM_ROLE"})
	void forbiddenRolesNeverResolveCompanyOrWrite(String role) {
		assertThrows(BadRequestException.class, () -> service.createByAdmin(request(role)));
		verifyNoInteractions(users, roles, companies, encoder, mapper);
	}

	@ParameterizedTest
	@ValueSource(strings = {"", "0", "99999999"})
	void unresolvedCompanyCannotWriteUserOrCompany(String id) {
		Role role = new Role();
		role.setCode("EMPLOYEE");
		when(roles.findByCode("EMPLOYEE")).thenReturn(Optional.of(role));
		properties.setId(id);
		if ("99999999".equals(id)) when(companies.findById(99999999L)).thenReturn(Optional.empty());
		assertThrows(BadRequestException.class, () -> service.createByAdmin(request("EMPLOYEE")));
		verifyNoInteractions(users, encoder, mapper);
		if ("99999999".equals(id)) verify(companies).findById(99999999L);
		verifyNoMoreInteractions(companies);
	}

	@Test
	void inactiveRoleCannotCreateAnAccount() {
		Role role = new Role();
		role.setCode("ACCOUNTANT");
		role.setActive(false);
		when(roles.findByCode("ACCOUNTANT")).thenReturn(Optional.of(role));
		assertThrows(BadRequestException.class, () -> service.createByAdmin(request("ACCOUNTANT")));
		verifyNoInteractions(users, companies, encoder, mapper);
	}

	private AdminCreateUserRequest request(String role) {
		return new AdminCreateUserRequest("new-account", "TestPassword123!", "Test", "new@example.test",
				null, role, 13L);
	}
}
