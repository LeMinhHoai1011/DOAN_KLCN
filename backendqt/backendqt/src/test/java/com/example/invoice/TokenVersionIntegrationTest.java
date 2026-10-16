package com.example.invoice;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.invoice.dto.auth.LoginRequest;
import com.example.invoice.dto.user.AdminUpdateUserRequest;
import com.example.invoice.dto.user.ChangePasswordRequest;
import com.example.invoice.entity.*;
import com.example.invoice.repository.UserRepository;
import com.example.invoice.security.JwtService;
import com.example.invoice.service.AuthService;
import com.example.invoice.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {"app.demo-admin.enabled=false", "app.demo-users.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(com.example.invoice.support.IsolatedAccountFixtures.class)
class TokenVersionIntegrationTest {
	@Autowired UserRepository users;
	@Autowired UserService service;
	@Autowired AuthService auth;
	@Autowired JwtService jwt;
	@Autowired PasswordEncoder encoder;
	@Autowired PlatformTransactionManager transactions;
	@Autowired EntityManager em;
	@Autowired MockMvc mvc;
	@Value("${app.jwt.secret}") String secret;
	private static final String OLD = "OldPassword123!";
	private static final String NEW = "NewPassword456!";

	private String account() {
		String name = "tv-" + UUID.randomUUID();
		new TransactionTemplate(transactions).executeWithoutResult(status -> {
			User user = new User(); user.setUsername(name); user.setFullName("Token version test");
			user.setEmail(name + "@example.test"); user.setPassword(encoder.encode(OLD)); users.saveAndFlush(user);
		});
		return name;
	}

	private UsernamePasswordAuthenticationToken actor(String name) {
		return new UsernamePasswordAuthenticationToken(name, "unused");
	}

	private long version(String name) {
		return new TransactionTemplate(transactions).execute(s -> users.findByUsername(name).orElseThrow().getTokenVersion());
	}

	private String token(String name, String tv, long epochSecond) {
		String claim = "MISSING".equals(tv) ? "" : ",\"tv\":" + tv;
		String payload = "{\"sub\":\"" + name + "\",\"iat\":" + epochSecond
				+ ",\"exp\":" + (epochSecond + 3600) + claim + "}";
		return Jwts.builder().content(payload).signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
	}

	private void access(String token, int expected) throws Exception {
		mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token)).andExpect(status().is(expected));
	}

	@ParameterizedTest
	@ValueSource(strings = {"MISSING", "\"0\"", "0.0", "-1", "9223372036854775808", "true", "null", "1"})
	void malformedMissingOrMismatchedVersionIs401(String claim) throws Exception {
		String name = account();
		access(token(name, claim, System.currentTimeMillis() / 1000), 401);
	}

	@Test
	void passwordChangeRevokesTwoTokensAndDistinguishesTokensInSameSecond() throws Exception {
		String name = account();
		String oldLogin = auth.login(new LoginRequest(name, OLD)).token();
		long second = System.currentTimeMillis() / 1000;
		String sameSecondOld = token(name, "0", second);
		access(oldLogin, 200); access(sameSecondOld, 200);
		mvc.perform(put("/api/v1/users/me/password").header("Authorization", "Bearer " + oldLogin)
				.contentType("application/json").content("{\"currentPassword\":\"" + OLD + "\",\"newPassword\":\"" + NEW + "\"}"))
				.andExpect(status().isNoContent());
		assertEquals(1L, version(name));
		access(oldLogin, 401); access(sameSecondOld, 401);
		assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> auth.login(new LoginRequest(name, OLD)));
		String fresh = auth.login(new LoginRequest(name, NEW)).token();
		access(fresh, 200); access(token(name, "1", second), 200);
		// A fresh service instance still rejects old versions; no in-memory revocation state.
		var snapshot = new com.example.invoice.security.VersionedUserDetails(name, "unused", true, List.of(), 1);
		assertFalse(new JwtService(secret, 60000).isTokenValid(oldLogin, snapshot));
		assertTrue(new JwtService(secret, 60000).isTokenValid(fresh, snapshot));
	}

	@Test
	void lockUnlockDoesNotRestoreOldTokensAndRepeatedStatusDoesNotIncrement() throws Exception {
		String name = account();
		String old = auth.login(new LoginRequest(name, OLD)).token();
		Long id = users.findByUsername(name).orElseThrow().getId();
		service.updateByAdmin(id, new AdminUpdateUserRequest(null, UserStatus.ACTIVE, null));
		assertEquals(0L, version(name));
		service.updateByAdmin(id, new AdminUpdateUserRequest(null, UserStatus.INACTIVE, null));
		service.updateByAdmin(id, new AdminUpdateUserRequest(null, UserStatus.INACTIVE, null));
		assertEquals(1L, version(name)); access(old, 401);
		service.updateByAdmin(id, new AdminUpdateUserRequest(null, UserStatus.ACTIVE, null));
		assertEquals(1L, version(name)); access(old, 401);
		access(auth.login(new LoginRequest(name, OLD)).token(), 200);
	}

	@Test
	void deletedUserTokenIs401() throws Exception {
		String name = account(); String token = auth.login(new LoginRequest(name, OLD)).token();
		new TransactionTemplate(transactions).executeWithoutResult(s -> users.delete(users.findByUsername(name).orElseThrow()));
		access(token, 401);
	}

	@Test
	void failedPasswordChangeAndRollbackPreserveCredentialsAndVersion() throws Exception {
		String name = account(); String old = auth.login(new LoginRequest(name, OLD)).token();
		assertThrows(com.example.invoice.exception.BadRequestException.class,
				() -> service.changePassword(actor(name), new ChangePasswordRequest("wrong", NEW)));
		assertEquals(0L, version(name));
		new TransactionTemplate(transactions).executeWithoutResult(s -> {
			service.changePassword(actor(name), new ChangePasswordRequest(OLD, NEW));
			users.flush(); assertEquals(1L, users.findByUsername(name).orElseThrow().getTokenVersion());
			s.setRollbackOnly();
		});
		assertEquals(0L, version(name)); access(old, 200);
		assertFalse(auth.login(new LoginRequest(name, OLD)).token().isBlank());
	}

	@ParameterizedTest
	@ValueSource(strings = {"password", "lock", "profile"})
	void concurrentWritesConflictInsteadOfLosingSecurityState(String other) throws Exception {
		String name = account();
		CyclicBarrier loaded = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Callable<Boolean>> jobs = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				final boolean first = i == 0;
				jobs.add(() -> {
					try {
						new TransactionTemplate(transactions).executeWithoutResult(s -> {
							User user = users.findByUsername(name).orElseThrow();
							try { loaded.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new RuntimeException(e); }
							if (first || "password".equals(other)) {
								service.changePassword(actor(name), new ChangePasswordRequest(OLD, NEW));
							} else if ("lock".equals(other)) {
								service.updateByAdmin(user.getId(), new AdminUpdateUserRequest(null, UserStatus.INACTIVE, null));
							} else {
								service.updateCurrent(actor(name), new com.example.invoice.dto.user.UpdateUserRequest("Changed", null, null, null));
							}
							users.flush();
						});
						return true;
					} catch (org.springframework.dao.OptimisticLockingFailureException | jakarta.persistence.OptimisticLockException conflict) {
						return false;
					}
				});
			}
			var results = executor.invokeAll(jobs, 20, TimeUnit.SECONDS);
			assertEquals(1, results.stream().filter(f -> { try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); } }).count());
			new TransactionTemplate(transactions).executeWithoutResult(s -> {
				User user = users.findByUsername(name).orElseThrow();
				assertEquals(1L, user.getRowVersion());
				boolean changed = encoder.matches(NEW, user.getPassword());
				assertEquals(changed || user.getStatus() == UserStatus.INACTIVE ? 1L : 0L, user.getTokenVersion());
				assertNull(user.getCompany()); assertTrue(user.getUserRoles().isEmpty());
			});
		} finally { executor.shutdownNow(); }
	}

	@Test
	void loginDuringUncommittedPasswordChangeCannotIssueNewVersionFromOldPassword() throws Exception {
		String name = account();
		CountDownLatch changed = new CountDownLatch(1), release = new CountDownLatch(1), loginStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> writer = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> {
				service.changePassword(actor(name), new ChangePasswordRequest(OLD, NEW));
				users.flush(); changed.countDown();
				try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
				catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
			}));
			assertTrue(changed.await(10, TimeUnit.SECONDS));
			Future<String> login = executor.submit(() -> { loginStarted.countDown(); return auth.login(new LoginRequest(name, OLD)).token(); });
			assertTrue(loginStarted.await(10, TimeUnit.SECONDS));
			try {
				assertThrows(TimeoutException.class, () -> login.get(200, TimeUnit.MILLISECONDS));
			} finally { release.countDown(); }
			writer.get(10, TimeUnit.SECONDS);
			ExecutionException failure = assertThrows(ExecutionException.class, () -> login.get(10, TimeUnit.SECONDS));
			assertInstanceOf(org.springframework.security.authentication.BadCredentialsException.class, failure.getCause());
			assertEquals(1L, version(name));
			access(auth.login(new LoginRequest(name, NEW)).token(), 200);
		} finally { release.countDown(); executor.shutdownNow(); }
	}

	@Test
	void tokenVersionOverflowCannotPartiallyChangePassword() {
		String name = account();
		new TransactionTemplate(transactions).executeWithoutResult(s -> {
			User user = users.findByUsername(name).orElseThrow();
			org.springframework.test.util.ReflectionTestUtils.setField(user, "tokenVersion", Long.MAX_VALUE);
		});
		assertThrows(com.example.invoice.exception.BadRequestException.class,
				() -> service.changePassword(actor(name), new ChangePasswordRequest(OLD, NEW)));
		assertEquals(Long.MAX_VALUE, version(name));
		assertTrue(encoder.matches(OLD, users.findByUsername(name).orElseThrow().getPassword()));
	}
}
