package com.shikhi.practice.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.practice.policy.Bucket;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure entity behavior — no Spring, no DB (mirrors {@code ReviewProgressTest}'s approach). */
class DailyLearningPlanTest {

	private DailyLearningPlan plan() {
		return new DailyLearningPlan(UUID.randomUUID(), LocalDate.parse("2026-07-12"), 100, 60, 25,
				15, Map.of());
	}

	@Test
	void startsActiveWithRemainingEqualToPlanned() {
		DailyLearningPlan plan = plan();

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
		assertThat(plan.getRemainingNew()).isEqualTo(60);
		assertThat(plan.getRemainingWeak()).isEqualTo(25);
		assertThat(plan.getRemainingReview()).isEqualTo(15);
	}

	@Test
	void consumeDecrementsOnlyTheMatchingBucket() {
		DailyLearningPlan plan = plan();

		plan.consume(Bucket.WEAK);

		assertThat(plan.getRemainingNew()).isEqualTo(60);
		assertThat(plan.getRemainingWeak()).isEqualTo(24);
		assertThat(plan.getRemainingReview()).isEqualTo(15);
	}

	@Test
	void consumeFloorsAtZeroInsteadOfGoingNegative() {
		DailyLearningPlan plan = new DailyLearningPlan(UUID.randomUUID(),
				LocalDate.parse("2026-07-12"), 1, 0, 0, 1, Map.of());

		plan.consume(Bucket.REVIEW);
		plan.consume(Bucket.REVIEW);

		assertThat(plan.getRemainingReview()).isZero();
	}

	@Test
	void completeMarksTheStatusAndStampsCompletedAt() {
		DailyLearningPlan plan = plan();
		Instant now = Instant.parse("2026-07-12T12:00:00Z");

		plan.complete(now);

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.COMPLETED);
		assertThat(plan.getCompletedAt()).isEqualTo(now);
	}

	@Test
	void completeIsIdempotentAndKeepsTheFirstTimestamp() {
		DailyLearningPlan plan = plan();
		Instant first = Instant.parse("2026-07-12T12:00:00Z");
		Instant later = Instant.parse("2026-07-12T13:00:00Z");

		plan.complete(first);
		plan.complete(later);

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.COMPLETED);
		assertThat(plan.getCompletedAt()).isEqualTo(first);
	}
}
