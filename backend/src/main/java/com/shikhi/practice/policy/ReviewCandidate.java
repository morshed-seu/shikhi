package com.shikhi.practice.policy;

import java.time.Instant;
import java.util.UUID;

/**
 * One REVIEW-bucket candidate (doc 42 §5/§9.4): a graduated word whose {@code dueAt} has
 * passed. Fetched by {@code plan.PlanCandidateRepository} — a plain data carrier, no JPA/SQL
 * knowledge — and ranked by {@link ReviewSelectionPolicy}.
 */
public record ReviewCandidate(UUID vocabularyId, Instant dueAt, int failureStreak) {
}
