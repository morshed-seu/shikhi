package com.shikhi.practice.schedule;

import java.time.Duration;

/**
 * Policy for how long a graduated word waits before its next review, keyed by
 * {@link ReviewProgress#getReviewStage()}. Kept as a seam (doc 43 §7 / doc 42 §7.2 future work)
 * so a fixed day-count ladder can later be swapped for an adaptive scheduler (e.g. FSRS)
 * without touching {@code WordProgressService} or the entity.
 */
public interface WordReviewScheduler {

	/** How long to wait before the word is due again once it reaches {@code stage}. */
	Duration interval(int stage);
}
