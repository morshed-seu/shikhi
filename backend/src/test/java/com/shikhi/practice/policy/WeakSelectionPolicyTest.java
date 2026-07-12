package com.shikhi.practice.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Doc 42 §9.5: priority {@code (5 - masteryScore) * 3 + failureStreak * 2 + recentMistakeBonus},
 * and the 60/40 current/earlier-band split (doc 42 §6.5) as a goal, not a hard rule.
 */
class WeakSelectionPolicyTest {

	private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

	private final WeakSelectionPolicy policy = new WeakSelectionPolicy();

	private WeakCandidate candidate(String band, int mastery, int failureStreak,
			Instant lastWrongAt) {
		return new WeakCandidate(UUID.randomUUID(), band, mastery, failureStreak, lastWrongAt);
	}

	@Test
	void ordersByDescendingPriority() {
		WeakCandidate low = candidate("A1", 4, 0, null); // (5-4)*3 = 3
		WeakCandidate high = candidate("A1", 0, 0, null); // (5-0)*3 = 15
		WeakCandidate mid = candidate("A1", 2, 2, null); // (5-2)*3 + 2*2 = 13

		List<WeakCandidate> selected = policy.select(List.of(low, mid, high), 3, NOW, "A1",
				List.of(), 60);

		assertThat(selected).containsExactly(high, mid, low);
	}

	@Test
	void failureStreakOutweighsMastery() {
		WeakCandidate manyFailures = candidate("A1", 5, 3, null); // 0 + 6 = 6
		WeakCandidate lowMasteryNoFailures = candidate("A1", 3, 0, null); // (5-3)*3 = 6
		WeakCandidate worseMastery = candidate("A1", 1, 0, null); // (5-1)*3 = 12

		List<WeakCandidate> selected = policy.select(
				List.of(manyFailures, lowMasteryNoFailures, worseMastery), 3, NOW, "A1", List.of(),
				60);

		assertThat(selected.get(0)).isEqualTo(worseMastery);
	}

	@Test
	void recentMistakeBonusAppliesExactlyAtTheThreeDayBoundary() {
		WeakCandidate exactlyThreeDaysAgo = candidate("A1", 5, 0, NOW.minus(Duration.ofDays(3)));
		WeakCandidate justOverThreeDaysAgo = candidate("A1", 5, 0,
				NOW.minus(Duration.ofDays(3)).minus(Duration.ofMillis(1)));

		List<WeakCandidate> selected = policy.select(
				List.of(justOverThreeDaysAgo, exactlyThreeDaysAgo), 2, NOW, "A1", List.of(), 60);

		// Both start at priority 0 (mastery 5, no failures); the bonus is the only tiebreaker,
		// so the boundary candidate (still "within" 3 days) must rank first.
		assertThat(selected).containsExactly(exactlyThreeDaysAgo, justOverThreeDaysAgo);
	}

	@Test
	void splitsSixtyFortyBetweenCurrentAndEarlierBands() {
		List<WeakCandidate> currentBand = List.of(
				candidate("A2", 5, 0, null), candidate("A2", 5, 0, null),
				candidate("A2", 5, 0, null), candidate("A2", 5, 0, null),
				candidate("A2", 5, 0, null), candidate("A2", 5, 0, null));
		List<WeakCandidate> earlierBand = List.of(
				candidate("A1", 5, 0, null), candidate("A1", 5, 0, null),
				candidate("A1", 5, 0, null), candidate("A1", 5, 0, null));
		List<WeakCandidate> all = new java.util.ArrayList<>();
		all.addAll(currentBand);
		all.addAll(earlierBand);

		List<WeakCandidate> selected = policy.select(all, 10, NOW, "A2", List.of("A1"), 60);

		assertThat(selected).hasSize(10);
		long fromCurrent = selected.stream().filter(c -> "A2".equals(c.cefrLevel())).count();
		long fromEarlier = selected.stream().filter(c -> "A1".equals(c.cefrLevel())).count();
		assertThat(fromCurrent).isEqualTo(6);
		assertThat(fromEarlier).isEqualTo(4);
	}

	@Test
	void fillsFromWhicheverSideHasCandidatesWhenTheOtherIsShort() {
		// Target 10, 60/40 -> wants 6 current / 4 earlier, but only 2 earlier-band candidates
		// exist. The remaining 8 must all come from the abundant current band.
		List<WeakCandidate> currentBand = java.util.stream.IntStream.range(0, 20)
				.mapToObj(i -> candidate("A2", 5, 0, null)).toList();
		List<WeakCandidate> earlierBand = List.of(
				candidate("A1", 5, 0, null), candidate("A1", 5, 0, null));
		List<WeakCandidate> all = new java.util.ArrayList<>();
		all.addAll(currentBand);
		all.addAll(earlierBand);

		List<WeakCandidate> selected = policy.select(all, 10, NOW, "A2", List.of("A1"), 60);

		assertThat(selected).hasSize(10);
		assertThat(selected.stream().filter(c -> "A1".equals(c.cefrLevel())).count()).isEqualTo(2);
		assertThat(selected.stream().filter(c -> "A2".equals(c.cefrLevel())).count()).isEqualTo(8);
	}

	@Test
	void neverSelectsMoreThanTargetOrDuplicates() {
		List<WeakCandidate> all = java.util.stream.IntStream.range(0, 5)
				.mapToObj(i -> candidate("A1", i, 0, null)).toList();

		List<WeakCandidate> selected = policy.select(all, 3, NOW, "A1", List.of(), 60);

		assertThat(selected).hasSize(3);
		assertThat(selected.stream().map(WeakCandidate::vocabularyId).collect(Collectors.toSet()))
				.hasSize(3);
	}

	@Test
	void emptyCandidatesYieldsEmptySelection() {
		assertThat(policy.select(List.of(), 5, NOW, "A1", List.of(), 60)).isEmpty();
	}

	@Test
	void nonPositiveTargetYieldsEmptySelection() {
		List<WeakCandidate> all = List.of(candidate("A1", 0, 0, null));
		assertThat(policy.select(all, 0, NOW, "A1", List.of(), 60)).isEmpty();
	}
}
