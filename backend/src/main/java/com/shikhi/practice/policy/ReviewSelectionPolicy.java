package com.shikhi.practice.policy;

import java.util.Comparator;
import java.util.List;

/**
 * REVIEW-bucket ranking (doc 42 §7.5): "priority = daysOverdue, higher wins" — most-overdue
 * words are selected first, up to {@code target}. Deliberately re-sorts rather than trusting
 * caller order, since {@code plan.PlanCandidateRepository} only promises a candidate *list*,
 * never a ranking (doc 42 §10).
 */
public final class ReviewSelectionPolicy {

	public List<ReviewCandidate> select(List<ReviewCandidate> candidates, int target) {
		if (target <= 0 || candidates.isEmpty()) {
			return List.of();
		}
		return candidates.stream()
				.sorted(Comparator.comparing(ReviewCandidate::dueAt))
				.limit(target)
				.toList();
	}
}
