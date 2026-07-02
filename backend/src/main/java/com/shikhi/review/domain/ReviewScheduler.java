package com.shikhi.review.domain;

import java.time.Duration;

/**
 * Leitner box intervals (LLD §2.6). Higher boxes mean the item is better known, so it is
 * scheduled further out. Box 1 is "just missed / still learning"; each correct recall
 * promotes one box up to {@link #MAX_BOX}, a miss drops back to box 1. Richer scheduling is a
 * post-pilot enhancement.
 */
public final class ReviewScheduler {

	public static final int MIN_BOX = 1;
	public static final int MAX_BOX = 5;

	// Indexed by box level (index 0 unused). Box 1 is due immediately, so a just-missed item
	// resurfaces in the next review session; each promotion pushes it further out.
	private static final Duration[] INTERVALS = {
			Duration.ZERO,
			Duration.ZERO,
			Duration.ofDays(1),
			Duration.ofDays(3),
			Duration.ofDays(7),
			Duration.ofDays(16),
	};

	private ReviewScheduler() {
	}

	public static int clampBox(int box) {
		return Math.max(MIN_BOX, Math.min(box, MAX_BOX));
	}

	public static Duration interval(int box) {
		return INTERVALS[clampBox(box)];
	}
}
