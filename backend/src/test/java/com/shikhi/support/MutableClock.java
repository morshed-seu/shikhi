package com.shikhi.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Clock} whose instant can be advanced mid-test without sleeping real time — needed
 * for multi-day scenarios (doc 43 §6 VE4: plan-date rollover, review-ladder due dates). Always
 * UTC, matching the production {@code TimeConfig} clock bean it overrides in tests.
 */
public final class MutableClock extends Clock {

	private final AtomicReference<Instant> instant;
	private final ZoneId zone;

	public MutableClock(Instant initial) {
		this(initial, ZoneId.of("UTC"));
	}

	private MutableClock(Instant initial, ZoneId zone) {
		this.instant = new AtomicReference<>(initial);
		this.zone = zone;
	}

	/** Move the clock forward — e.g. {@code advance(Duration.ofDays(1))} to roll to "tomorrow". */
	public void advance(Duration duration) {
		instant.updateAndGet(current -> current.plus(duration));
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(instant.get(), zone);
	}

	@Override
	public Instant instant() {
		return instant.get();
	}
}
