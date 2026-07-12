package com.shikhi.practice.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.practice.policy.Bucket;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DailyLearningPlanItemTest {

	@Test
	void startsPending() {
		DailyLearningPlanItem item = new DailyLearningPlanItem(UUID.randomUUID(),
				UUID.randomUUID(), Bucket.NEW, 0);

		assertThat(item.getStatus()).isEqualTo(ItemStatus.PENDING);
		assertThat(item.getConsumedSessionId()).isNull();
		assertThat(item.getConsumedAt()).isNull();
	}

	@Test
	void consumeStampsSessionAndCompletesTheItem() {
		DailyLearningPlanItem item = new DailyLearningPlanItem(UUID.randomUUID(),
				UUID.randomUUID(), Bucket.REVIEW, 3);
		UUID sessionId = UUID.randomUUID();
		Instant now = Instant.parse("2026-07-12T12:00:00Z");

		item.consume(sessionId, now);

		assertThat(item.getStatus()).isEqualTo(ItemStatus.COMPLETED);
		assertThat(item.getConsumedSessionId()).isEqualTo(sessionId);
		assertThat(item.getConsumedAt()).isEqualTo(now);
	}
}
