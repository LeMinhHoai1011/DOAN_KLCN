package com.example.invoice;

import static org.junit.jupiter.api.Assertions.*;
import com.example.invoice.dto.user.*;
import com.example.invoice.entity.*;
import com.example.invoice.repository.*;
import com.example.invoice.service.UserService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {"app.demo-admin.enabled=false", "app.demo-users.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:admin_guard_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"})
@ActiveProfiles("test")
@Import(com.example.invoice.support.IsolatedAccountFixtures.class)
class AdminGuardConcurrencyTest {
	@Autowired UserRepository users;
	@Autowired RoleRepository roles;
	@Autowired UserService service;
	@Autowired PlatformTransactionManager transactions;
	private String first, second;
	private Long firstId, secondId, employeeId;

	@BeforeEach
	void isolatedPairOfActiveAdmins() {
		new TransactionTemplate(transactions).executeWithoutResult(s -> {
			for (User u : users.findAll()) u.setStatus(UserStatus.INACTIVE);
			Role admin = roles.findByCode("ADMIN").orElseThrow();
			employeeId = roles.findByCode("EMPLOYEE").orElseThrow().getId();
			first = "guard-a-" + UUID.randomUUID(); second = "guard-b-" + UUID.randomUUID();
			firstId = create(first, admin).getId(); secondId = create(second, admin).getId();
			users.flush(); assertEquals(2L, users.countActiveAdmins());
		});
	}

	@ParameterizedTest @ValueSource(strings = {"lock", "demote", "mixed"})
	void mutualConcurrentChangesLeaveExactlyOneActiveAdmin(String mode) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CyclicBarrier start = new CyclicBarrier(2);
		try {
			var futures = executor.invokeAll(List.of(operation(first, secondId, mode, true, start),
					operation(second, firstId, mode, false, start)), 20, TimeUnit.SECONDS);
			int success = 0;
			for (Future<Boolean> f : futures) if (f.get()) success++;
			assertEquals(1, success);
			assertEquals(1L, users.countActiveAdmins());
		} finally { executor.shutdownNow(); }
	}

	@Test
	void inactiveAdminsAreExcludedFromCountAndCannotDisableRemainingAdmin() {
		new TransactionTemplate(transactions).executeWithoutResult(s -> users.findById(firstId).orElseThrow().setStatus(UserStatus.INACTIVE));
		assertEquals(1L, users.countActiveAdmins());
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(first, "unused"));
		try {
			assertThrows(org.springframework.security.access.AccessDeniedException.class,
					() -> service.updateByAdmin(secondId, new AdminUpdateUserRequest(null, UserStatus.INACTIVE, null)));
			assertEquals(1L, users.countActiveAdmins());
		} finally { SecurityContextHolder.clearContext(); }
	}

	private Callable<Boolean> operation(String caller, Long target, String mode, boolean firstOperation, CyclicBarrier start) {
		return () -> {
			SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(caller, "unused"));
			try {
				start.await(10, TimeUnit.SECONDS);
				if ("lock".equals(mode) || ("mixed".equals(mode) && firstOperation))
					service.updateByAdmin(target, new AdminUpdateUserRequest(null, UserStatus.INACTIVE, null));
				else service.assignRoles(target, new AssignRolesRequest(Set.of(employeeId)));
				return true;
			} catch (org.springframework.security.access.AccessDeniedException | com.example.invoice.exception.BadRequestException rejected) {
				return false;
			} finally { SecurityContextHolder.clearContext(); }
		};
	}

	private User create(String name, Role admin) {
		User u = new User(); u.setUsername(name); u.setFullName("Guard test"); u.setEmail(name + "@example.test"); u.setPassword("test-only");
		var a = new UserRoleAssignment(); a.setUser(u); a.setRole(admin); u.getUserRoles().add(a); return users.saveAndFlush(u);
	}
}
