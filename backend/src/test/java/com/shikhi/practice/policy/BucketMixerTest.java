package com.shikhi.practice.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Doc 42 §8.3: max-3-in-a-row interleaving, and that mixing preserves each bucket's ratio
 * (it only ever reorders, never drops or adds items).
 */
class BucketMixerTest {

	private final BucketMixer mixer = new BucketMixer();

	private List<UUID> ids(int count) {
		return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
	}

	@Test
	void preservesTheCountOfEachBucket() {
		List<UUID> newIds = ids(12);
		List<UUID> weakIds = ids(5);
		List<UUID> reviewIds = ids(3);

		List<BucketMixer.MixedItem> mixed = mixer.mix(newIds, weakIds, reviewIds, 3, new Random(1));

		assertThat(mixed).hasSize(20);
		assertThat(mixed.stream().filter(m -> m.bucket() == Bucket.NEW).count()).isEqualTo(12);
		assertThat(mixed.stream().filter(m -> m.bucket() == Bucket.WEAK).count()).isEqualTo(5);
		assertThat(mixed.stream().filter(m -> m.bucket() == Bucket.REVIEW).count()).isEqualTo(3);
	}

	@Test
	void everyIdSurvivesMixingExactlyOnce() {
		List<UUID> newIds = ids(4);
		List<UUID> weakIds = ids(3);
		List<UUID> reviewIds = ids(2);

		List<BucketMixer.MixedItem> mixed = mixer.mix(newIds, weakIds, reviewIds, 3, new Random(7));

		assertThat(mixed.stream().map(BucketMixer.MixedItem::vocabularyId).toList())
				.containsExactlyInAnyOrderElementsOf(
						java.util.stream.Stream.of(newIds, weakIds, reviewIds)
								.flatMap(List::stream).toList());
	}

	@Test
	void neverExceedsMaxConsecutiveRunAcrossManySeeds() {
		// Balanced-ish bucket sizes so the mixer always has an alternative and the 3-cap is
		// fully achievable (doc 42 §8.3's "when possible" caveat only bites once one bucket is
		// exhausted and the others aren't — avoided here by keeping sizes close).
		List<UUID> newIds = ids(10);
		List<UUID> weakIds = ids(9);
		List<UUID> reviewIds = ids(8);

		for (long seed = 0; seed < 25; seed++) {
			List<BucketMixer.MixedItem> mixed = mixer.mix(newIds, weakIds, reviewIds, 3,
					new Random(seed));
			assertThat(maxRun(mixed)).as("seed %d", seed).isLessThanOrEqualTo(3);
		}
	}

	@Test
	void singleBucketNeverProducesAnIllegalRunLongerThanItsOwnSize() {
		// Degenerate case: only one bucket has items. The 3-cap can't be honored (doc 42 §8.3
		// "when possible") — the whole thing is necessarily one run.
		List<BucketMixer.MixedItem> mixed = mixer.mix(ids(6), List.of(), List.of(), 3,
				new Random(1));
		assertThat(mixed).hasSize(6);
		assertThat(maxRun(mixed)).isEqualTo(6);
	}

	private int maxRun(List<BucketMixer.MixedItem> mixed) {
		int max = 0;
		int current = 0;
		Bucket previous = null;
		for (BucketMixer.MixedItem item : mixed) {
			current = item.bucket() == previous ? current + 1 : 1;
			previous = item.bucket();
			max = Math.max(max, current);
		}
		return max;
	}
}
