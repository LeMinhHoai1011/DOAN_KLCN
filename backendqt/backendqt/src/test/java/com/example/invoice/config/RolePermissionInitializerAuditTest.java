package com.example.invoice.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** No datasource or repository is needed to initialize RBAC safely. */
@ExtendWith(OutputCaptureExtension.class)
class RolePermissionInitializerAuditTest {
    @ParameterizedTest
    @ValueSource(strings = {"default", "development", "production", "demo"})
    void startupCannotProvisionOrMigrateOnAnyProfile(String profile, CapturedOutput output) {
        new ApplicationContextRunner().withBean(RolePermissionInitializer.class)
                .withPropertyValues("spring.profiles.active=" + profile).run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(RolePermissionInitializer.class).run();
                    context.getBean(RolePermissionInitializer.class).run();
                    assertThat(output).contains("RBAC bootstrap skipped reason=EXPLICIT_PROVISIONING_REQUIRED");
                });
    }

    @Test
    void initializerHasNoDatabaseOrRepositoryDependencies() {
        assertThat(RolePermissionInitializer.class.getDeclaredFields())
                .allMatch(field -> java.lang.reflect.Modifier.isStatic(field.getModifiers()));
        assertThat(RolePermissionInitializer.class.getDeclaredConstructors()).hasSize(1);
        assertThat(RolePermissionInitializer.class.getDeclaredConstructors()[0].getParameterCount()).isZero();
    }
}
