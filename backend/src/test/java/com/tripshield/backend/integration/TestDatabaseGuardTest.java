package com.tripshield.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestDatabaseGuardTest {
    private final TestDatabaseGuard guard = new TestDatabaseGuard();

    @Test
    void noExplicitProfileLoadsLocalConfigurationWithoutOpeningADatabaseConnection() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        ConfigDataEnvironmentPostProcessor.applyTo(environment);

        assertEquals("local", environment.getDefaultProfiles()[0]);
        assertEquals(0, environment.getActiveProfiles().length);
        assertNotNull(environment.getProperty("spring.datasource.url"));
    }

    @Test
    void guardIsAutomaticallyLoadedOnTheTestClasspath() {
        assertTrue(SpringFactoriesLoader.loadFactories(ApplicationContextInitializer.class,
                getClass().getClassLoader()).stream().anyMatch(TestDatabaseGuard.class::isInstance));
    }

    @Test
    void acceptsOnlyThePinnedTestDatasource() {
        assertDoesNotThrow(() -> guard.initialize(context("test",
                "jdbc:mysql://127.0.0.1:3306/tripshield_test", "tripshield_test_app")));
    }

    @Test
    void rejectsLocalProfileEvenWithTestDatasource() {
        assertThrows(IllegalStateException.class, () -> guard.initialize(context("local",
                "jdbc:mysql://127.0.0.1:3306/tripshield_test", "tripshield_test_app")));
    }

    @Test
    void rejectsTestAndLocalProfilesTogether() {
        assertThrows(IllegalStateException.class, () -> guard.initialize(context("test,local",
                "jdbc:mysql://127.0.0.1:3306/tripshield_test", "tripshield_test_app")));
    }

    @Test
    void rejectsDevelopmentDatasourceUnderTestProfile() {
        assertThrows(IllegalStateException.class, () -> guard.initialize(context("test",
                "jdbc:mysql://localhost:3306/tripshield", "root")));
    }

    @Test
    void rejectsWrongUserUnderTestProfile() {
        assertThrows(IllegalStateException.class, () -> guard.initialize(context("test",
                "jdbc:mysql://127.0.0.1:3306/tripshield_test", "root")));
    }

    private GenericApplicationContext context(String profile, String url, String user) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(profile.split(","));
        environment.getPropertySources().addFirst(new MapPropertySource("test-values", Map.of(
                "spring.datasource.url", url, "spring.datasource.username", user)));
        GenericApplicationContext context = new GenericApplicationContext();
        context.setEnvironment(environment);
        return context;
    }
}
