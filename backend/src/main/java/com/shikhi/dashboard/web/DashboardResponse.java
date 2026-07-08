package com.shikhi.dashboard.web;

import com.shikhi.progress.web.Stats;
import java.util.List;

/**
 * One-round-trip learner dashboard snapshot (contract {@code DashboardResponse}, E13).
 * Read-only composition of stats, per-band word mastery, review load, and lifetime totals.
 */
public record DashboardResponse(Stats stats, List<WordMasteryEntry> wordMastery,
		int reviewDueCount, int lessonsCompleted, int practiceSessionsCompleted,
		int totalAnswered, int totalCorrect) {
}
