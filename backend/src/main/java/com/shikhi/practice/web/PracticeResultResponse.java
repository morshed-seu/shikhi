package com.shikhi.practice.web;

import com.shikhi.progress.web.Stats;

/** Contract {@code PracticeResult} — session totals returned by complete (idempotent). */
public record PracticeResultResponse(int correctCount, int totalCount, int roundsPlayed,
		int xpEarned, boolean levelUpEligible, Stats stats) {
}
