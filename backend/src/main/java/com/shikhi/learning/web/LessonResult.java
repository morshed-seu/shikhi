package com.shikhi.learning.web;

import com.shikhi.progress.web.Stats;
import java.util.List;

/**
 * Contract {@code LessonResult}. {@code reviewItemsAdded} is always zero until the review
 * queue ships (M6). {@code xpEarned} is the XP awarded for this completion (0 when replaying
 * an already-completed lesson); the cross-session XP total lives in {@code stats}.
 */
public record LessonResult(int score, int xpEarned, List<String> newlyUnlocked,
		int reviewItemsAdded, Stats stats) {
}
