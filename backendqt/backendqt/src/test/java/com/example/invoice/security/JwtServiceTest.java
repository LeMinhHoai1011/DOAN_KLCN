package com.example.invoice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtServiceTest {
	@Test
	void generatesAndValidatesJwtForTheAuthenticatedUser() {
		JwtService jwtService = new JwtService("test-jwt-secret-key-that-is-long-enough-for-hmac", 60_000L);
		UserDetails user = new User("alice", "ignored", List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
		UserDetails anotherUser = new User("bob", "ignored", List.of());

		String token = jwtService.generateToken(user);

		assertEquals("alice", jwtService.extractUsername(token));
		assertTrue(jwtService.isTokenValid(token, user));
		assertFalse(jwtService.isTokenValid(token, anotherUser));
	}
}
