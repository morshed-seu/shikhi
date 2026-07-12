package com.shikhi.practice.policy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Interleaves the three bucket selections into one serve order at plan-creation time (doc 42
 * §8.3): repeatedly pick the largest remaining bucket that differs from the previous pick,
 * capped at {@code maxConsecutive} same-bucket items in a row. Within-bucket order is
 * preserved (review candidates already arrive most-overdue-first, weak candidates
 * priority-first) — mixing only interleaves across buckets, it never re-ranks within one.
 *
 * <p>{@code random} only breaks ties when two or more eligible buckets have the same number
 * of items left; it never reorders a bucket's own items or changes the overall bucket ratio.
 * When only one bucket still has items left, the cap can't be honored (doc 42 §8.3 "when
 * possible") — the tail is simply that bucket's remaining run.
 */
public final class BucketMixer {

	public record MixedItem(Bucket bucket, UUID vocabularyId) {
	}

	public List<MixedItem> mix(List<UUID> newIds, List<UUID> weakIds, List<UUID> reviewIds,
			int maxConsecutive, Random random) {
		Map<Bucket, Deque<UUID>> queues = new EnumMap<>(Bucket.class);
		queues.put(Bucket.NEW, new ArrayDeque<>(newIds));
		queues.put(Bucket.WEAK, new ArrayDeque<>(weakIds));
		queues.put(Bucket.REVIEW, new ArrayDeque<>(reviewIds));

		List<MixedItem> result = new ArrayList<>(newIds.size() + weakIds.size() + reviewIds.size());
		Bucket previous = null;
		int streak = 0;

		while (queues.values().stream().anyMatch(q -> !q.isEmpty())) {
			Bucket next = pickNext(queues, previous, streak, maxConsecutive, random);
			result.add(new MixedItem(next, queues.get(next).poll()));
			if (next == previous) {
				streak++;
			}
			else {
				previous = next;
				streak = 1;
			}
		}
		return result;
	}

	private Bucket pickNext(Map<Bucket, Deque<UUID>> queues, Bucket previous, int streak,
			int maxConsecutive, Random random) {
		List<Bucket> eligible = queues.entrySet().stream()
				.filter(e -> !e.getValue().isEmpty())
				.map(Map.Entry::getKey)
				.toList();

		boolean mustSwitch = previous != null && streak >= maxConsecutive;
		List<Bucket> candidates = eligible;
		if (mustSwitch) {
			List<Bucket> withoutPrevious = eligible.stream().filter(b -> b != previous).toList();
			if (!withoutPrevious.isEmpty()) {
				candidates = withoutPrevious;
			}
			// else: previous is the only bucket left with items — no alternative exists.
		}

		int maxSize = candidates.stream().mapToInt(b -> queues.get(b).size()).max().orElseThrow();
		List<Bucket> tied = candidates.stream()
				.filter(b -> queues.get(b).size() == maxSize)
				.toList();
		return tied.size() == 1 ? tied.get(0) : tied.get(random.nextInt(tied.size()));
	}
}
