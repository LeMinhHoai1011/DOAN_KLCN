package com.example.invoice.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.jsonwebtoken.MalformedJwtException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class JwtAuthenticationFilterTest {
	private final JwtService jwt = mock(JwtService.class);
	private final CustomUserDetailsService users = mock(CustomUserDetailsService.class);
	private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);

	@BeforeEach
	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@ParameterizedTest
	@ValueSource(strings = {"disabled", "locked", "expired", "credentialsExpired"})
	void rejectsInvalidCurrentAccountState(String state) throws Exception {
		var builder = User.withUsername("alice").password("unused").roles("EMPLOYEE");
		switch (state) {
			case "disabled" -> builder.disabled(true);
			case "locked" -> builder.accountLocked(true);
			case "expired" -> builder.accountExpired(true);
			default -> builder.credentialsExpired(true);
		}
		when(jwt.extractUsername("token")).thenReturn("alice");
		when(users.loadUserByUsername("alice")).thenReturn(builder.build());
		request(false);
		verify(jwt, never()).isTokenValid(anyString(), any());
	}

	@Test
	void deletedAccountContinuesUnauthenticatedInsteadOfThrowing() throws Exception {
		when(jwt.extractUsername("token")).thenReturn("deleted");
		when(users.loadUserByUsername("deleted")).thenThrow(new UsernameNotFoundException("missing"));
		request(false);
	}

	@Test
	void activeAccountIsAuthenticatedWithCurrentAuthorities() throws Exception {
		UserDetails user = new User("alice", "unused", List.of());
		when(jwt.extractUsername("token")).thenReturn("alice");
		when(users.loadUserByUsername("alice")).thenReturn(user);
		when(jwt.isTokenValid("token", user)).thenReturn(true);
		request(true);
		assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
	}

	@Test
	void eachRequestReloadsStateIncludingAcrossFilterInstances() throws Exception {
		UserDetails active = User.withUsername("alice").password("unused").roles("EMPLOYEE").build();
		UserDetails disabled = User.withUsername("alice").password("unused").roles("EMPLOYEE").disabled(true).build();
		when(jwt.extractUsername("token")).thenReturn("alice");
		when(users.loadUserByUsername("alice")).thenReturn(active, disabled);
		when(jwt.isTokenValid("token", active)).thenReturn(true);
		request(true);
		SecurityContextHolder.clearContext();
		new JwtAuthenticationFilter(jwt, users).doFilter(request(), new MockHttpServletResponse(), (req, res) ->
				assertNull(SecurityContextHolder.getContext().getAuthentication()));
		verify(users, times(2)).loadUserByUsername("alice");
	}

	@Test
	void malformedTokenCannotAuthenticate() throws Exception {
		when(jwt.extractUsername("token")).thenThrow(new MalformedJwtException("invalid"));
		request(false);
		verifyNoInteractions(users);
	}

	private MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("Authorization", "Bearer token");
		return request;
	}

	private void request(boolean authenticated) throws Exception {
		boolean[] continued = {false};
		filter.doFilter(request(), new MockHttpServletResponse(), (req, res) -> {
			continued[0] = true;
			assertEquals(authenticated, SecurityContextHolder.getContext().getAuthentication() != null);
		});
		assertTrue(continued[0]);
	}
}
