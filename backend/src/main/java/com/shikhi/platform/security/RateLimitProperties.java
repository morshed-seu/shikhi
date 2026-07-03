package com.shikhi.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Abuse-protection limits for authentication endpoints (NFR-SEC5, threat model §S/§D).
 * A per-client token bucket throttles credential stuffing and login brute force.
 *
 * <p>This is an in-process limiter — correct for a single instance and the pilot. Scaling the
 * API horizontally needs a shared (Redis) bucket so the limit is global; the {@code enabled}
 * flag and this seam make that swap localized. See docs/90-runbook.md.
 */
@ConfigurationProperties(prefix = "shikhi.security.rate-limit")
public class RateLimitProperties {

	/** Master switch (default on; tests/tools can disable). */
	private boolean enabled = true;

	/** Max requests allowed in a burst per client for the guarded endpoints. */
	private int capacity = 10;

	/** Tokens refilled per {@link #refillPeriodSeconds} (sustained rate). */
	private int refillTokens = 10;

	/** Refill window in seconds. */
	private long refillPeriodSeconds = 60;

	/** Safety cap on tracked clients to bound memory; evicted wholesale when exceeded. */
	private int maxTrackedClients = 100_000;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getCapacity() {
		return capacity;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public int getRefillTokens() {
		return refillTokens;
	}

	public void setRefillTokens(int refillTokens) {
		this.refillTokens = refillTokens;
	}

	public long getRefillPeriodSeconds() {
		return refillPeriodSeconds;
	}

	public void setRefillPeriodSeconds(long refillPeriodSeconds) {
		this.refillPeriodSeconds = refillPeriodSeconds;
	}

	public int getMaxTrackedClients() {
		return maxTrackedClients;
	}

	public void setMaxTrackedClients(int maxTrackedClients) {
		this.maxTrackedClients = maxTrackedClients;
	}
}
