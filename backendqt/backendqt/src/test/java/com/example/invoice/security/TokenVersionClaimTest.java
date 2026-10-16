package com.example.invoice.security;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenVersionClaimTest {
	@Test
	void largeBigintVersionSurvivesSigningAndParsingWithoutPasswordClaims() throws Exception {
		JwtService jwt = new JwtService("test-jwt-secret-key-that-is-long-enough-for-hmac", 60000);
		var user = new VersionedUserDetails("alice", "private-password-hash", true, List.of(), Long.MAX_VALUE);
		String token = jwt.generateToken(user);
		assertTrue(jwt.isTokenValid(token, user));
		String payload = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
		assertFalse(payload.contains("private-password-hash"));
		assertFalse(payload.contains("password"));
		assertThrows(IllegalArgumentException.class, () -> jwt.generateToken(
				new org.springframework.security.core.userdetails.User("alice", "hash", List.of())));
	}
}
