package com.shikhi.platform.security;

/**
 * A minimal thread-safe token bucket. Tokens refill continuously at {@code refillTokens /
 * refillPeriodNanos}; {@link #tryConsume()} takes one token if available. Used to rate-limit a
 * single client key (see {@link RateLimitFilter}).
 */
final class TokenBucket {

	private final long capacity;
	private final double refillPerNano;
	private double tokens;
	private long lastRefillNanos;

	TokenBucket(long capacity, long refillTokens, long refillPeriodNanos) {
		this.capacity = capacity;
		this.refillPerNano = (double) refillTokens / refillPeriodNanos;
		this.tokens = capacity;
		this.lastRefillNanos = System.nanoTime();
	}

	synchronized boolean tryConsume() {
		refill();
		if (tokens >= 1.0) {
			tokens -= 1.0;
			return true;
		}
		return false;
	}

	private void refill() {
		long now = System.nanoTime();
		double refilled = (now - lastRefillNanos) * refillPerNano;
		if (refilled > 0) {
			tokens = Math.min(capacity, tokens + refilled);
			lastRefillNanos = now;
		}
	}
}
