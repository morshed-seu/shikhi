package com.shikhi.platform.data;

import org.flywaydb.core.api.exception.FlywayValidateException;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Dev-only Flyway recovery. The local database is disposable and entirely migration/seed-driven,
 * so a checksum mismatch — which happens when a seed migration is edited after it was already
 * applied locally (common while iterating on seed data) — should self-heal instead of aborting
 * {@code bootRun} with "Migration checksum mismatch". On a validation failure we clean the schema
 * and re-apply every migration from scratch, rebuilding the seed data cleanly.
 *
 * <p>Scoped to the {@code dev} profile only (the default for {@code bootRun}; see build.gradle).
 * Production keeps {@code spring.flyway.clean-disabled=true}, so {@link org.flywaydb.core.Flyway#clean()}
 * can never run there; integration tests use fresh Testcontainers and so never hit a mismatch.
 * Flyway 10+ removed the old {@code cleanOnValidationError} flag, so this restores that behavior
 * explicitly and narrowly (only a validation failure triggers the rebuild, never a real migration
 * error).
 */
@Configuration
@Profile("dev")
public class DevFlywayRecoveryConfig {

	@Bean
	public FlywayMigrationStrategy cleanAndRemigrateOnValidationError() {
		return flyway -> {
			try {
				flyway.migrate();
			} catch (FlywayValidateException ex) {
				// Local dev DB only: wipe and rebuild from migrations so a checksum mismatch
				// never blocks startup. Safe because there is no data here that migrations
				// don't recreate.
				flyway.clean();
				flyway.migrate();
			}
		};
	}
}
