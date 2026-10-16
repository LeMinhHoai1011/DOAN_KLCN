package com.example.invoice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.invoice.dto.auth.RegisterRequest;
import com.example.invoice.repository.RoleRepository;
import com.example.invoice.repository.UserRepository;
import com.example.invoice.security.CustomUserDetailsService;
import com.example.invoice.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

class PublicRegistrationDisabledTest {
	@Test
	void directRegistrationRejectsWithoutAnyRepositoryAuthenticationOrJwtInteraction() {
		UserRepository users = mock(UserRepository.class);
		RoleRepository roles = mock(RoleRepository.class);
		PasswordEncoder encoder = mock(PasswordEncoder.class);
		AuthenticationManager authentication = mock(AuthenticationManager.class);
		JwtService jwt = mock(JwtService.class);
		UserMapper mapper = mock(UserMapper.class);
		CustomUserDetailsService details = mock(CustomUserDetailsService.class);
		AuthService service = new AuthService(users, roles, encoder, authentication, jwt, mapper, details);
		RegisterRequest request = new RegisterRequest("registration-test", "TestPassword123!",
				"Test", "registration@example.test", null);
		for (RegisterRequest candidate : new RegisterRequest[] {request, null}) {
			var exception = assertThrows(AuthService.PublicRegistrationDisabledException.class,
					() -> service.register(candidate));
			assertTrue(exception.getMessage().startsWith("PUBLIC_REGISTRATION_DISABLED:"));
		}
		verifyNoInteractions(users, roles, encoder, authentication, jwt, mapper, details);
	}
}
