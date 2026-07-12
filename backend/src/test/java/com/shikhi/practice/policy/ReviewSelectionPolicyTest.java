package com.shikhi.practice.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Doc 42 §7.5: priority = daysOverdue, most overdue first, regardless of input order. */
class ReviewSelectionPolicyTest {

	private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

	private final ReviewSelectionPolicy policy = new ReviewSelectionPolicy();

	private ReviewCandidate candidate(Instant dueAt) {
		return new ReviewCandidate(UUID.randomUUID(), dueAt, 0);
	}

	@Test
	void selectsMostOverdueFirstRegardlessOfInputOrder() {
		ReviewCandidate dueToday = candidate(NOW);
		ReviewCandidate dueLastWeek = candidate(NOW.minus(Duration.ofDays(7)));
		ReviewCandidate dueYesterday = candidate(NOW.minus(Duration.ofDays(1)));

		List<ReviewCandidate> selected = policy.select(
				List.of(dueToday, dueLastWeek, dueYesterday), 3);

		assertThat(selected).containsExactly(dueLastWeek, dueYesterday, dueToday);
	}

	@Test
	void capsAtTarget() {
		ReviewCandidate mostOverdue = candidate(NOW.minus(Duration.ofDays(3)));
		List<ReviewCandidate> candidates = List.of(
				candidate(NOW.minus(Duration.ofDays(1))), candidate(NOW.minus(Duration.ofDays(2))),
				mostOverdue);

		assertThat(policy.select(candidates, 1)).containsExactly(mostOverdue);
	}

	@Test
	void nonPositiveTargetYieldsEmptySelection() {
		assertThat(policy.select(List.of(candidate(NOW)), 0)).isEmpty();
	}

	@Test
	void emptyCandidatesYieldsEmptySelection() {
		assertThat(policy.select(List.of(), 5)).isEmpty();
	}
}
