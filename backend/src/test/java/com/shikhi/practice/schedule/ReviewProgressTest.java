package com.shikhi.practice.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Ladder arithmetic (doc 43 §3): promote/demote counters and the demote floor. */
class ReviewProgressTest {

	private final UUID userId = UUID.randomUUID();
	private final UUID vocabularyId = UUID.randomUUID();
	private final Instant now = Instant.parse("2026-07-12T00:00:00Z");

	@Test
	void promoteAdvancesStageAndResetsFailureStreak() {
		ReviewProgress review = new ReviewProgress(userId, vocabularyId, 2, now);
		// Simulate a prior failure streak so promote's reset is actually observable.
		review.demote(now, Duration.ofDays(1));
		assertThat(review.getFailureStreak()).isEqualTo(1);

		Instant later = now.plus(Duration.ofDays(1));
		review.promote(later, Duration.ofDays(7));

		assertThat(review.getReviewStage()).isEqualTo(1); // demote(2->0) then promote(0->1)
		assertThat(review.getDueAt()).isEqualTo(later.plus(Duration.ofDays(7)));
		assertThat(review.getLastReviewedAt()).isEqualTo(later);
		assertThat(review.getReviewCount()).isEqualTo(2);
		assertThat(review.getSuccessfulReviews()).isEqualTo(1);
		assertThat(review.getFailedReviews()).isEqualTo(1);
		assertThat(review.getFailureStreak()).isZero();
		assertThat(review.getUpdatedAt()).isEqualTo(later);
	}

	@Test
	void demoteFloorsAtZero() {
		ReviewProgress review = new ReviewProgress(userId, vocabularyId, 1, now);

		review.demote(now, Duration.ofDays(1));

		assertThat(review.getReviewStage()).isZero(); // max(0, 1 - 2)
		assertThat(review.getFailedReviews()).isEqualTo(1);
		assertThat(review.getFailureStreak()).isEqualTo(1);
		assertThat(review.getLastFailureAt()).isEqualTo(now);
		assertThat(review.getDueAt()).isEqualTo(now.plus(Duration.ofDays(1)));
	}

	@Test
	void demoteFromHighStageSubtractsTwo() {
		ReviewProgress review = new ReviewProgress(userId, vocabularyId, 5, now);

		review.demote(now, Duration.ofDays(3));

		assertThat(review.getReviewStage()).isEqualTo(3);
	}

	@Test
	void isDueComparesAgainstDueAt() {
		ReviewProgress review = new ReviewProgress(userId, vocabularyId, 1, now);

		assertThat(review.isDue(now)).isTrue(); // due at == now
		assertThat(review.isDue(now.minusSeconds(1))).isFalse();
		assertThat(review.isDue(now.plusSeconds(1))).isTrue();
	}
}
