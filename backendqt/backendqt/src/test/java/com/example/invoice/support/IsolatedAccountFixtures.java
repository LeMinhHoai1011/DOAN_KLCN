package com.example.invoice.support;

import com.example.invoice.entity.User;
import com.example.invoice.entity.UserRoleAssignment;
import com.example.invoice.repository.UserRepository;
import com.example.invoice.repository.RoleRepository;
import javax.sql.DataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Test-only fixture restricted to in-memory H2; never packaged with the application. */
@TestConfiguration(proxyBeanMethods = false)
public class IsolatedAccountFixtures {
	@Bean
	@Order(3)
	CommandLineRunner isolatedAccounts(DataSource dataSource, UserRepository users, RoleRepository roles,
			PasswordEncoder encoder, PlatformTransactionManager transactions,
			com.example.invoice.repository.PermissionGroupRepository groups,
			com.example.invoice.repository.PermissionRepository permissions) {
		return args -> {
			try (var connection = dataSource.getConnection()) {
				if (!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:")) {
					throw new IllegalStateException("Account fixtures require an in-memory H2 database");
				}
			}
			new TransactionTemplate(transactions).executeWithoutResult(status -> {
				new IsolatedRbacFixtures(roles, groups, permissions).initialize();
				String[][] accounts = {{"admin", "Admin@123456", "ADMIN"},
						{"accountant", "Accountant@123", "ACCOUNTANT"}, {"employee", "Employee@123", "EMPLOYEE"}};
				for (String[] account : accounts) {
					if (users.findByUsername(account[0]).isPresent()) continue;
					User user = new User();
					user.setUsername(account[0]);
					user.setPassword(encoder.encode(account[1]));
					user.setFullName("Isolated test " + account[2]);
					user.setEmail(account[0] + "@example.test");
					UserRoleAssignment assignment = new UserRoleAssignment();
					assignment.setUser(user);
					assignment.setRole(roles.findByCode(account[2]).orElseThrow());
					user.getUserRoles().add(assignment);
					users.save(user);
				}
			});
		};
	}
}
