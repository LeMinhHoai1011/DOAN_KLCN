package com.example.invoice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.invoice.repository.CompanyRepository;
import com.example.invoice.service.InternalCompanyService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class InternalCompanyConfigurationTest {
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(BindingConfiguration.class)
			.withBean(InternalCompanyService.class)
			.withBean(CompanyRepository.class, () -> mock(CompanyRepository.class));

	@Test
	void contextStartsWithoutProvisioningOrRepositoryAccess() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(InternalCompanyService.class).isConfigured()).isFalse();
			verifyNoInteractions(context.getBean(CompanyRepository.class));
		});
	}

	@Test
	void emptyPlaceholderStartsWithoutProvisioning() {
		runner.withPropertyValues("app.internal-company.id=${G1_TEST_ABSENT_COMPANY_ID:}").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(InternalCompanyProperties.class).getId()).isEmpty();
			assertThat(context.getBean(InternalCompanyService.class).isConfigured()).isFalse();
			verifyNoInteractions(context.getBean(CompanyRepository.class));
		});
	}

	@Test
	void invalidConfigurationDoesNotBreakStartup() {
		runner.withPropertyValues("app.internal-company.id=invalid").run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context.getBean(InternalCompanyService.class).isConfigured()).isFalse();
			verifyNoInteractions(context.getBean(CompanyRepository.class));
		});
	}

	@Test
	void environmentVariableBindsThroughConfiguredPlaceholder() {
		runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
				new SystemEnvironmentPropertySource("testEnvironment", Map.of("INTERNAL_COMPANY_ID", "42"))))
				.withPropertyValues("app.internal-company.id=${INTERNAL_COMPANY_ID:}").run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context.getBean(InternalCompanyProperties.class).getId()).isEqualTo("42");
					assertThat(context.getBean(InternalCompanyService.class).isConfigured()).isTrue();
					verifyNoInteractions(context.getBean(CompanyRepository.class));
				});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(InternalCompanyProperties.class)
	static class BindingConfiguration {
	}
}
