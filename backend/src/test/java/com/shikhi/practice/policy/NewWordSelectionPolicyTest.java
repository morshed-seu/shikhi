package com.shikhi.practice.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Doc 42 §6.5/§13.5: 90/10 current/earlier-band split for new words, with shortfall-filling. */
class NewWordSelectionPolicyTest {

	private final NewWordSelectionPolicy policy = new NewWordSelectionPolicy();

	private NewCandidate candidate(String band) {
		return new NewCandidate(UUID.randomUUID(), band);
	}

	@Test
	void splitsNinetyTenBetweenCurrentAndEarlierBands() {
		List<NewCandidate> currentBand = IntStream.range(0, 50)
				.mapToObj(i -> candidate("A2")).toList();
		List<NewCandidate> earlierBand = IntStream.range(0, 50)
				.mapToObj(i -> candidate("A1")).toList();
		List<NewCandidate> all = new ArrayList<>();
		all.addAll(currentBand);
		all.addAll(earlierBand);

		List<NewCandidate> selected = policy.select(all, 20, "A2", List.of("A1"), 90);

		assertThat(selected).hasSize(20);
		assertThat(selected.stream().filter(c -> "A2".equals(c.cefrLevel())).count()).isEqualTo(18);
		assertThat(selected.stream().filter(c -> "A1".equals(c.cefrLevel())).count()).isEqualTo(2);
	}

	@Test
	void fillsFromCurrentBandWhenEarlierBandIsShort() {
		List<NewCandidate> currentBand = IntStream.range(0, 50)
				.mapToObj(i -> candidate("A2")).toList();
		List<NewCandidate> earlierBand = List.of(candidate("A1")); // only 1, target wants 2

		List<NewCandidate> all = new ArrayList<>();
		all.addAll(currentBand);
		all.addAll(earlierBand);

		List<NewCandidate> selected = policy.select(all, 20, "A2", List.of("A1"), 90);

		assertThat(selected).hasSize(20);
		assertThat(selected.stream().filter(c -> "A1".equals(c.cefrLevel())).count()).isEqualTo(1);
		assertThat(selected.stream().filter(c -> "A2".equals(c.cefrLevel())).count()).isEqualTo(19);
	}

	@Test
	void preservesPreShuffledOrderWithinABand() {
		// The SQL layer already randomizes order (order by random()); the policy must not
		// reorder within a band, only apply the split.
		List<NewCandidate> currentBand = IntStream.range(0, 5)
				.mapToObj(i -> candidate("A2")).toList();

		List<NewCandidate> selected = policy.select(currentBand, 3, "A2", List.of(), 90);

		assertThat(selected).containsExactly(currentBand.get(0), currentBand.get(1),
				currentBand.get(2));
	}

	@Test
	void neverSelectsMoreThanTargetOrDuplicates() {
		List<NewCandidate> all = IntStream.range(0, 5).mapToObj(i -> candidate("A1")).toList();

		List<NewCandidate> selected = policy.select(all, 3, "A1", List.of(), 90);

		assertThat(selected).hasSize(3);
		assertThat(selected.stream().map(NewCandidate::vocabularyId).collect(Collectors.toSet()))
				.hasSize(3);
	}

	@Test
	void emptyCandidatesYieldsEmptySelection() {
		assertThat(policy.select(List.of(), 5, "A1", List.of(), 90)).isEmpty();
	}

	@Test
	void nonPositiveTargetYieldsEmptySelection() {
		assertThat(policy.select(List.of(candidate("A1")), 0, "A1", List.of(), 90)).isEmpty();
	}
}
