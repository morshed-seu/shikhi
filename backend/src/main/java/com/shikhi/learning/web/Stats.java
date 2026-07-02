package com.shikhi.learning.web;

import java.util.Map;

/**
 * Contract {@code Stats}. In M3 only the session-scoped fields are meaningful — {@code hearts}
 * (lives left this play-through). The cross-session gamification fields ({@code xp},
 * {@code rank}, streaks, {@code dailyGoal}, {@code accuracyByPattern}) are populated in M4;
 * they are present now so the response shape stays stable.
 */
public record Stats(int xp, int rank, int currentStreak, int longestStreak, int hearts,
		int dailyGoal, Map<String, Double> accuracyByPattern) {

	/** M3 stats: only hearts are tracked yet; gamification fields default to zero/empty. */
	public static Stats forHearts(int hearts) {
		return new Stats(0, 0, 0, 0, hearts, 0, Map.of());
	}
}
