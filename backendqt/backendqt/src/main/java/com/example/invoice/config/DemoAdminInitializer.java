package com.example.invoice.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Demo seeding stays fail-closed until independent datasource verification is approved. */
@Component
@org.springframework.core.annotation.Order(3)
@RequiredArgsConstructor
public class DemoAdminInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoAdminInitializer.class);
    private final Environment environment;

    @Value("${app.demo-seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        var profiles = java.util.Arrays.asList(environment.getActiveProfiles());
        String reason;
        if (profiles.stream().anyMatch(profile -> java.util.Set.of("prod", "production").contains(profile))) {
            reason = "PRODUCTION_PROFILE";
        } else if (!profiles.contains("demo")) {
            reason = "DEMO_PROFILE_REQUIRED";
        } else if (!seedEnabled) {
            reason = "SEED_DISABLED";
        } else {
            // A profile, URL, hostname or flag cannot attest database isolation.
            // No repository/JDBC dependency or write path is available in this phase.
            reason = "DEMO_DATASOURCE_VERIFICATION_UNAVAILABLE";
        }
        log.info("Demo seed skipped reason={}; legacy demo flags cannot enable seeding", reason);
    }
}
