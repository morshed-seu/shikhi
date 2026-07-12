package com.shikhi.practice.schedule;

import com.shikhi.practice.config.PlannerProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Fixed day-count ladder (doc 42 §7.2, doc 43 §3): {@code [0, 1, 3, 7, 14, 30, 60, 120, 180,
 * 365]} days by default, configurable via {@code shikhi.practice.planner.review-intervals-days}.
 * Stage 0 means "due immediately" (freshly demoted words); each later stage widens the gap.
 * A stage outside the configured range clamps to the nearest end rather than erroring, so a
 * runaway {@code reviewStage} (promotions never re-clamp it — see {@link ReviewProgress#promote})
 * degrades gracefully to the longest configured interval.
 */
@Component
public class FixedIntervalScheduler implements WordReviewScheduler {

	private final List<Integer> ladderDays;

	public FixedIntervalScheduler(PlannerProperties properties) {
		this.ladderDays = properties.getReviewIntervalsDays();
	}

	@Override
	public Duration interval(int stage) {
		int clamped = Math.max(0, Math.min(stage, maxStage()));
		return Duration.ofDays(ladderDays.get(clamped));
	}

	/**
	 * Highest meaningful stage — {@link #interval(int)} clamps to this. Not part of {@link
	 * WordReviewScheduler} (doc 43 §4 Fix 7b: no caller outside this class needed it on the
	 * interface); kept public since {@code FixedIntervalSchedulerTest} asserts on it directly.
	 */
	public int maxStage() {
		return ladderDays.size() - 1;
	}
}
