package com.tripshield.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = com.tripshield.backend.integration.TestDatabaseGuard.class)
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
