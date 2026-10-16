package com.example.invoice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.invoice.repository.CompanyRepository;
import com.example.invoice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class DemoAdminInitializerTest {
	@ParameterizedTest
	@CsvSource(value = {"production;true;PRODUCTION_PROFILE", "prod;true;PRODUCTION_PROFILE",
			"development;true;DEMO_PROFILE_REQUIRED", "default;true;DEMO_PROFILE_REQUIRED",
			"demo;false;SEED_DISABLED", "demo;true;DEMO_DATASOURCE_VERIFICATION_UNAVAILABLE",
			"demo,production;true;PRODUCTION_PROFILE"}, delimiter = ';')
	void refusesSeedingWithoutApprovedDatabaseVerification(String profiles, boolean enabled, String reason,
			CapturedOutput output) {
		UserRepository users = mock(UserRepository.class);
		CompanyRepository companies = mock(CompanyRepository.class);
		new ApplicationContextRunner().withBean(DemoAdminInitializer.class)
				.withBean(UserRepository.class, () -> users).withBean(CompanyRepository.class, () -> companies)
				.withPropertyValues("spring.profiles.active=" + profiles, "app.demo-seed.enabled=" + enabled,
						"app.demo-admin.enabled=true", "app.demo-users.enabled=true")
				.run(context -> {
					assertThat(context).hasNotFailed();
					context.getBean(DemoAdminInitializer.class).run();
					context.getBean(DemoAdminInitializer.class).run();
					verifyNoInteractions(users, companies);
					assertThat(output).contains("Demo seed skipped reason=" + reason);
				});
	}

	@Test
	void defaultsToDisabledEvenOnDemoProfile(CapturedOutput output) {
		new ApplicationContextRunner().withBean(DemoAdminInitializer.class)
				.withPropertyValues("spring.profiles.active=demo").run(context -> {
					context.getBean(DemoAdminInitializer.class).run();
					assertThat(output).contains("Demo seed skipped reason=SEED_DISABLED");
				});
	}
}
