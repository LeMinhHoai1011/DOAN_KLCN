package com.example.invoice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Startup never provisions RBAC, executes DDL, or migrates existing user assignments. */
@Component
@Order(2)
public class RolePermissionInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(RolePermissionInitializer.class);

    @Override
    public void run(String... args) {
        log.info("RBAC bootstrap skipped reason=EXPLICIT_PROVISIONING_REQUIRED; existing roles and permissions are preserved");
    }
}
