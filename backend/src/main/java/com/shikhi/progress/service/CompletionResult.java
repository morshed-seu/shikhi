package com.shikhi.progress.service;

import com.shikhi.progress.web.Stats;
import java.util.List;

/** Outcome of completing a lesson: XP awarded now, lessons unlocked, and the fresh stats. */
public record CompletionResult(int xpEarned, List<String> newlyUnlocked, Stats stats) {
}
