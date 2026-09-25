package com.example.invoice.config;

import com.example.invoice.entity.User;
import com.example.invoice.entity.UserRole;
import com.example.invoice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@org.springframework.core.annotation.Order(1)
@RequiredArgsConstructor
public class DemoAdminInitializer implements CommandLineRunner {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;

	@Value("${app.demo-admin.enabled:true}")
	private boolean enabled;

	@Value("${app.demo-admin.username:admin}")
	private String username;

	@Value("${app.demo-admin.password:Admin@123456}")
	private String password;

	@Value("${app.demo-admin.email:admin@smartinvoice.local}")
	private String email;

	@Value("${app.demo-users.enabled:true}")
	private boolean demoUsersEnabled;

	@Override
	public void run(String... args) {
		allowLegacyPasswordColumnToBeEmpty();
		allowSupportedRoles();

		if (enabled) {
			createIfMissing(username, password, "Smart Invoice Admin", email, UserRole.ADMIN);
		}
		if (demoUsersEnabled) {
			createIfMissing("accountant", "Accountant@123", "Smart Invoice Accountant", "accountant@smartinvoice.local", UserRole.ACCOUNTANT);
			createIfMissing("employee", "Employee@123", "Smart Invoice Employee", "employee@smartinvoice.local", UserRole.EMPLOYEE);
		}
	}

	private void allowSupportedRoles() {
		try {
			jdbcTemplate.execute("alter table users drop constraint if exists users_role_check");
			jdbcTemplate.execute("alter table users add constraint users_role_check check (role in ('ADMIN', 'ACCOUNTANT', 'EMPLOYEE', 'USER'))");
		} catch (DataAccessException ignored) {
		}
	}

	private void createIfMissing(String accountUsername, String accountPassword, String fullName, String accountEmail, UserRole role) {
		if (userRepository.existsByUsername(accountUsername)) {
			return;
		}
		User user = new User();
		user.setUsername(accountUsername);
		user.setPassword(passwordEncoder.encode(accountPassword));
		user.setFullName(fullName);
		user.setEmail(accountEmail);
		user.setRole(role);
		userRepository.save(user);
	}

	private void allowLegacyPasswordColumnToBeEmpty() {
		try {
			Boolean exists = jdbcTemplate.queryForObject(
					"select exists (select 1 from information_schema.columns where table_name = 'users' and column_name = 'password')",
					Boolean.class);
			if (Boolean.TRUE.equals(exists)) {
				jdbcTemplate.execute("alter table users alter column password drop not null");
			}
		} catch (DataAccessException ignored) {
		}
	}
}
