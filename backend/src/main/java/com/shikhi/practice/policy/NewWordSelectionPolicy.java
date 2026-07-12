package com.shikhi.practice.policy;

import java.util.List;

/**
 * NEW-bucket ranking (doc 42 §6.5/§13.5): {@code currentBandPercent} (90) of {@code target}
 * from the learner's current band, the rest from earlier bands — "new vocabulary should
 * mostly match current ability." Candidates arrive pre-shuffled ({@code order by random()} in
 * {@code plan.PlanCandidateRepository}), so this policy only filters into bands and applies the
 * split ({@link BandSplitSelector}, shared with {@link WeakSelectionPolicy}); it never reorders
 * within a band.
 */
public final class NewWordSelectionPolicy {

	public List<NewCandidate> select(List<NewCandidate> candidates, int target,
			String currentBand, List<String> earlierBands, int currentBandPercent) {
		if (target <= 0 || candidates.isEmpty()) {
			return List.of();
		}

		List<NewCandidate> currentPool = candidates.stream()
				.filter(c -> currentBand.equals(c.cefrLevel())).toList();
		List<NewCandidate> earlierPool = candidates.stream()
				.filter(c -> earlierBands.contains(c.cefrLevel())).toList();

		return BandSplitSelector.select(currentPool, earlierPool, candidates, target,
				currentBandPercent, NewCandidate::vocabularyId);
	}
}
