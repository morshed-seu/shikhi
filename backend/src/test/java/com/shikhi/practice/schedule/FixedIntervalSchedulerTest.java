package com.shikhi.practice.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.practice.config.PlannerProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Ladder lookup + clamping (doc 42 §7.2, doc 43 §3 default ladder). */
class FixedIntervalSchedulerTest {

	@Test
	void looksUpDefaultLadderByStage() {
		FixedIntervalScheduler scheduler = new FixedIntervalScheduler(new PlannerProperties());

		assertThat(scheduler.interval(0)).isEqualTo(Duration.ofDays(0));
		assertThat(scheduler.interval(1)).isEqualTo(Duration.ofDays(1));
		assertThat(scheduler.interval(2)).isEqualTo(Duration.ofDays(3));
		assertThat(scheduler.interval(9)).isEqualTo(Duration.ofDays(365));
		assertThat(scheduler.maxStage()).isEqualTo(9);
	}

	@Test
	void clampsNegativeStageToZero() {
		FixedIntervalScheduler scheduler = new FixedIntervalScheduler(new PlannerProperties());

		assertThat(scheduler.interval(-5)).isEqualTo(Duration.ofDays(0));
	}

	@Test
	void clampsStageBeyondMaxToTheLastEntry() {
		FixedIntervalScheduler scheduler = new FixedIntervalScheduler(new PlannerProperties());

		assertThat(scheduler.interval(50)).isEqualTo(Duration.ofDays(365));
	}

	@Test
	void honoursConfiguredLadderOverride() {
		PlannerProperties properties = new PlannerProperties();
		properties.setReviewIntervalsDays(List.of(0, 2, 5));
		FixedIntervalScheduler scheduler = new FixedIntervalScheduler(properties);

		assertThat(scheduler.maxStage()).isEqualTo(2);
		assertThat(scheduler.interval(1)).isEqualTo(Duration.ofDays(2));
		assertThat(scheduler.interval(2)).isEqualTo(Duration.ofDays(5));
		assertThat(scheduler.interval(10)).isEqualTo(Duration.ofDays(5)); // clamps to override max
	}
}
