package com.shikhi.practice.policy;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * WEAK-bucket ranking (doc 42 §9.5): {@code (5 - masteryScore) * 3 + failureStreak * 2 +
 * recentMistakeBonus}, applied within a CEFR split — a goal, not a hard rule (doc 42 §6.5):
 * {@code currentBandPercent} of {@code target} from the learner's current band, the rest from
 * earlier bands, filling from whichever side has candidates left if the other comes up short
 * ({@link BandSplitSelector}, shared with {@link NewWordSelectionPolicy}). {@code now} is
 * passed in rather than read from a clock — pure, deterministic, no I/O.
 */
public final class WeakSelectionPolicy {

	/** Doc 43 deviation #6: a failure within this window nudges priority instead of an event log. */
	private static final Duration RECENT_MISTAKE_WINDOW = Duration.ofDays(3);

	public List<WeakCandidate> select(List<WeakCandidate> candidates, int target, Instant now,
			String currentBand, List<String> earlierBands, int currentBandPercent) {
		if (target <= 0 || candidates.isEmpty()) {
			return List.of();
		}

		List<WeakCandidate> byPriority = candidates.stream()
				.sorted(Comparator.comparingDouble((WeakCandidate c) -> priority(c, now)).reversed())
				.toList();
		List<WeakCandidate> currentPool = byPriority.stream()
				.filter(c -> currentBand.equals(c.cefrLevel())).toList();
		List<WeakCandidate> earlierPool = byPriority.stream()
				.filter(c -> earlierBands.contains(c.cefrLevel())).toList();

		// Shortfall fallback is byPriority (not the raw candidates list), so a backfilled slot
		// still lands in priority order regardless of band.
		return BandSplitSelector.select(currentPool, earlierPool, byPriority, target,
				currentBandPercent, WeakCandidate::vocabularyId);
	}

	/** Package-visible for direct assertions in tests. */
	static double priority(WeakCandidate c, Instant now) {
		double p = (5 - c.masteryScore()) * 3.0 + c.failureStreak() * 2.0;
		if (c.lastWrongAt() != null && !c.lastWrongAt().isBefore(now.minus(RECENT_MISTAKE_WINDOW))) {
			p += 2;
		}
		return p;
	}
}
