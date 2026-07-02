package com.shikhi.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for integration tests that need the real data tier. Uses a single PostgreSQL
 * container shared JVM-wide (started once, reused by every subclass context) so Flyway
 * migrations run against a real Postgres — matching production (ADR-0003).
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

	public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}
}
