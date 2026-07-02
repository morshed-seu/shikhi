package com.shikhi.learning.web;

import java.util.List;

/**
 * Contract {@code LessonResult}. In M3 {@code newlyUnlocked} and {@code reviewItemsAdded} are
 * always empty/zero — lesson unlocking and the review queue arrive in M4/M6. {@code xpEarned}
 * is this session's XP; the cross-session XP total lives in {@code stats} from M4.
 */
public record LessonResult(int score, int xpEarned, List<String> newlyUnlocked,
		int reviewItemsAdded, Stats stats) {
}
