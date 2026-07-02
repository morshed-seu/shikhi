package com.shikhi.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the daily streak + hearts rollover (LLD §7). No database. */
class UserStatsTest {

	private final LocalDate day1 = LocalDate.of(2026, 7, 3);

	@Test
	void newLearnerStartsWithFullHeartsAndNoStreak() {
		UserStats s = new UserStats(UUID.randomUUID());
		assertThat(s.getHearts()).isEqualTo(UserStats.MAX_HEARTS);
		assertThat(s.getCurrentStreak()).isZero();
		assertThat(s.getXp()).isZero();
	}

	@Test
	void firstActivityStartsAStreakOfOne() {
		UserStats s = new UserStats(UUID.randomUUID());
		s.registerActiveDay(day1);
		assertThat(s.getCurrentStreak()).isEqualTo(1);
		assertThat(s.getLongestStreak()).isEqualTo(1);
	}

	@Test
	void sameDayActivityDoesNotRefillHeartsOrAdvanceStreak() {
		UserStats s = new UserStats(UUID.randomUUID());
		s.registerActiveDay(day1);
		s.loseHeart();
		s.loseHeart();
		assertThat(s.getHearts()).isEqualTo(3);

		s.registerActiveDay(day1); // same day again
		assertThat(s.getHearts()).isEqualTo(3); // not refilled
		assertThat(s.getCurrentStreak()).isEqualTo(1);
	}

	@Test
	void consecutiveDayAdvancesStreakAndRefillsHearts() {
		UserStats s = new UserStats(UUID.randomUUID());
		s.registerActiveDay(day1);
		s.loseHeart();
		s.registerActiveDay(day1.plusDays(1));
		assertThat(s.getCurrentStreak()).isEqualTo(2);
		assertThat(s.getHearts()).isEqualTo(UserStats.MAX_HEARTS);
	}

	@Test
	void missedDayResetsStreakButKeepsLongest() {
		UserStats s = new UserStats(UUID.randomUUID());
		s.registerActiveDay(day1);
		s.registerActiveDay(day1.plusDays(1)); // streak 2
		s.registerActiveDay(day1.plusDays(3)); // gap → reset
		assertThat(s.getCurrentStreak()).isEqualTo(1);
		assertThat(s.getLongestStreak()).isEqualTo(2);
	}

	@Test
	void heartsNeverGoNegative() {
		UserStats s = new UserStats(UUID.randomUUID());
		s.registerActiveDay(day1);
		for (int i = 0; i < 10; i++) {
			s.loseHeart();
		}
		assertThat(s.getHearts()).isZero();
	}
}
