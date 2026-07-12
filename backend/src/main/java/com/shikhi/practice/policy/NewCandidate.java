package com.shikhi.practice.policy;

import java.util.UUID;

/**
 * One NEW-bucket candidate (doc 42 §5): a word the learner has never practiced. Fetched by
 * {@code plan.PlanCandidateRepository} in random order (SQL {@code order by random()}) so
 * {@link NewWordSelectionPolicy} never needs its own shuffling.
 */
public record NewCandidate(UUID vocabularyId, String cefrLevel) {
}
