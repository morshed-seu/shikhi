package com.shikhi.practice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * {@link PlannerConfig}'s startup validation (doc 43 §4 Fix 6): both the base New/Weak/Review
 * split and the backlog-protection split must sum to exactly 100, or {@code AllocationPolicy}
 * silently over- or under-plans every learner's day. Unit-tested directly against {@link
 * PlannerConfig#validate}, no Spring context needed.
 */
class PlannerConfigTest {

	@Test
	void defaultPropertiesPassValidation() {
		assertThatCode(() -> PlannerConfig.validate(new PlannerProperties())).doesNotThrowAnyException();
	}

	@Test
	void rejectsABaseTripleThatDoesNotSumToOneHundred() {
		PlannerProperties properties = new PlannerProperties();
		properties.setNewPercent(60);
		properties.setWeakPercent(25);
		properties.setReviewPercent(10); // 60 + 25 + 10 = 95, not 100

		assertThatThrownBy(() -> PlannerConfig.validate(properties))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("new-percent/weak-percent/review-percent")
				.hasMessageContaining("95");
	}

	@Test
	void rejectsABacklogTripleThatDoesNotSumToOneHundred() {
		PlannerProperties properties = new PlannerProperties();
		properties.setBacklogNewPercent(5);
		properties.setBacklogWeakPercent(25);
		properties.setBacklogReviewPercent(71); // sums to 101

		assertThatThrownBy(() -> PlannerConfig.validate(properties))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("backlog-new-percent/backlog-weak-percent/backlog-review-percent")
				.hasMessageContaining("101");
	}

	@Test
	void acceptsACustomButValidSplit() {
		PlannerProperties properties = new PlannerProperties();
		properties.setNewPercent(50);
		properties.setWeakPercent(30);
		properties.setReviewPercent(20);

		assertThat(properties.getNewPercent() + properties.getWeakPercent()
				+ properties.getReviewPercent()).isEqualTo(100);
		assertThatCode(() -> PlannerConfig.validate(properties)).doesNotThrowAnyException();
	}
}
