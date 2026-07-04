package com.shikhi.progress.web;

import com.shikhi.progress.domain.UserStats;
import java.util.Map;

/**
 * Contract {@code Stats} — a learner's gamification snapshot. {@code rank} (leaderboard) and
 * {@code accuracyByPattern} are populated post-pilot; they are present now so the shape is
 * stable and default to {@code 0}/empty.
 */
public record Stats(int xp, int rank, int currentStreak, int longestStreak, int hearts,
		int dailyGoal, String cefrLevel, Map<String, Double> accuracyByPattern) {

	public static Stats from(UserStats s) {
		return new Stats(s.getXp(), s.getRank(), s.getCurrentStreak(), s.getLongestStreak(),
				s.getHearts(), s.getDailyGoal(), s.getCefrLevel(), Map.of());
	}
}
