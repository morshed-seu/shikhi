package com.shikhi.practice.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link PlannerProperties}, mirroring how {@code SecurityConfig} enables
 * {@code RateLimitProperties}/{@code JwtProperties} for the identity module — a small
 * dedicated {@code @Configuration} class local to the owning module rather than a shared
 * properties-scan.
 *
 * <p>Also fails application startup fast (doc 43 §4 Fix 6) if the configured percent triples
 * don't sum the way {@code AllocationPolicy}/{@code RedistributionPolicy} assume: a
 * mis-configured {@code new-percent}/{@code weak-percent}/{@code review-percent} (or the
 * backlog triple) would otherwise silently over- or under-plan every learner's day rather than
 * error anywhere visible.
 */
@Configuration
@EnableConfigurationProperties(PlannerProperties.class)
public class PlannerConfig implements InitializingBean {

	private final PlannerProperties properties;

	public PlannerConfig(PlannerProperties properties) {
		this.properties = properties;
	}

	@Override
	public void afterPropertiesSet() {
		validate(properties);
	}

	/** Package-private so it can be unit-tested directly, without booting a Spring context. */
	static void validate(PlannerProperties properties) {
		requireSumsTo100("new-percent/weak-percent/review-percent", properties.getNewPercent(),
				properties.getWeakPercent(), properties.getReviewPercent());
		requireSumsTo100("backlog-new-percent/backlog-weak-percent/backlog-review-percent",
				properties.getBacklogNewPercent(), properties.getBacklogWeakPercent(),
				properties.getBacklogReviewPercent());
	}

	private static void requireSumsTo100(String triple, int a, int b, int c) {
		int sum = a + b + c;
		if (sum != 100) {
			throw new IllegalStateException(
					"shikhi.practice.planner." + triple + " must sum to 100, but got " + sum
							+ " (" + a + " + " + b + " + " + c + ")");
		}
	}
}
