package com.example.invoice.config;

import com.example.invoice.entity.User;
import com.example.invoice.entity.Role;
import com.example.invoice.entity.UserRoleAssignment;
import com.example.invoice.repository.RoleRepository;
import com.example.invoice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@org.springframework.core.annotation.Order(3)
@RequiredArgsConstructor
public class DemoAdminInitializer implements CommandLineRunner {
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final JdbcTemplate jdbcTemplate;

	@Value("${app.demo-admin.enabled:false}")
	private boolean enabled;

	@Value("${app.demo-admin.username:admin}")
	private String username;

	@Value("${app.demo-admin.password:}")
	private String password;

	@Value("${app.demo-admin.email:admin@smartinvoice.local}")
	private String email;

	@Value("${app.demo-users.enabled:false}")
	private boolean demoUsersEnabled;

	@Override
	@Transactional
	public void run(String... args) {
		if (!enabled && !demoUsersEnabled) return;
		allowLegacyPasswordColumnToBeEmpty();
		if (enabled) {
			createIfMissing(username, password, "Smart Invoice Admin", email, "ADMIN");
		}
		if (demoUsersEnabled) {
			createIfMissing("accountant", "Accountant@123", "Smart Invoice Accountant", "accountant@smartinvoice.local", "ACCOUNTANT");
			createIfMissing("employee", "Employee@123", "Smart Invoice Employee", "employee@smartinvoice.local", "EMPLOYEE");
		}
	}

	private void createIfMissing(String accountUsername, String accountPassword, String fullName, String accountEmail, String roleCode) {
		User user = userRepository.findByUsername(accountUsername).orElseGet(() -> {
			User newUser = new User();
			newUser.setUsername(accountUsername);
			newUser.setPassword(passwordEncoder.encode(accountPassword));
			newUser.setFullName(fullName);
			newUser.setEmail(accountEmail);
			return userRepository.save(newUser);
		});
		Role databaseRole = roleRepository.findByCode(roleCode).orElse(null);
		if (databaseRole != null && user.getUserRoles().stream()
				.noneMatch(assignment -> assignment.getRole().getId().equals(databaseRole.getId()))) {
			UserRoleAssignment assignment = new UserRoleAssignment();
			assignment.setUser(user);
			assignment.setRole(databaseRole);
			user.getUserRoles().add(assignment);
		}
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
