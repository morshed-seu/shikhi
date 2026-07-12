package com.shikhi.platform.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Platform-wide {@link Clock} bean, UTC (consistent with {@code plan_date} and streak-day
 * boundaries computed in UTC — doc 43 deviation #8). Introduced for VE2 so time-dependent
 * scheduling logic ({@code WordProgressService}, {@code FixedIntervalScheduler}) can be
 * unit-tested with {@link Clock#fixed}; existing call sites that already use
 * {@code Instant.now()} directly are left as-is (only new code injects {@link Clock}).
 */
@Configuration
public class TimeConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
