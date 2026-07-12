package com.shikhi.practice.policy;

import java.time.Instant;
import java.util.UUID;

/**
 * One WEAK-bucket candidate (doc 42 §5/§9.5, computed — never persisted): a word with a low
 * {@code masteryScore} or an active review {@code failureStreak}. Fetched by
 * {@code plan.PlanCandidateRepository} and ranked by {@link WeakSelectionPolicy}.
 */
public record WeakCandidate(UUID vocabularyId, String cefrLevel, int masteryScore,
		int failureStreak, Instant lastWrongAt) {
}
