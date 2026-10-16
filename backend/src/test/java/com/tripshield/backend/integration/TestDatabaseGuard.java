package com.tripshield.backend.integration;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Fail before bean creation if a test profile is pointed at anything but the test database. */
public final class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final String EXPECTED_URL = "jdbc:mysql://127.0.0.1:3306/tripshield_test";
    private static final String EXPECTED_USER = "tripshield_test_app";

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");
        String user = context.getEnvironment().getProperty("spring.datasource.username", "");
        if (!EXPECTED_URL.equals(url) || !EXPECTED_USER.equals(user)) {
            throw new IllegalStateException("Integration tests require the pinned tripshield_test datasource and restricted test user");
        }
    }
}
