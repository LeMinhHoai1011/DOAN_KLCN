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

	@Override
	public void run(String... args) {
		allowLegacyPasswordColumnToBeEmpty();

		if (!enabled || userRepository.existsByUsername(username)) {
			return;
		}

		User admin = new User();
		admin.setUsername(username);
		admin.setPassword(passwordEncoder.encode(password));
		admin.setFullName("Smart Invoice Admin");
		admin.setEmail(email);
		admin.setRole(UserRole.ADMIN);
		userRepository.save(admin);
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