package com.tripshield.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication application = new SpringApplication(BackendApplication.class);
		application.addInitializers(context -> {
			if (!java.util.Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("test")) return;
			String url = context.getEnvironment().getProperty("spring.datasource.url", "");
			String user = context.getEnvironment().getProperty("spring.datasource.username", "");
			if (!"jdbc:mysql://127.0.0.1:3306/tripshield_test".equals(url) || !"tripshield_test_app".equals(user))
				throw new IllegalStateException("Test profile requires the isolated tripshield_test database and restricted user");
		});
		application.run(args);
	}

}
