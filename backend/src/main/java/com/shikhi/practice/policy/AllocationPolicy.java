package com.shikhi.practice.policy;

import java.util.Comparator;
import java.util.List;

/**
 * Turns the daily capacity into per-bucket targets (doc 42 §6.2-§6.4, doc 43 §3): a
 * percentage split of {@code capacity}, switched to a review-heavy split under backlog
 * protection, then capped to what {@link RedistributionPolicy} finds is actually available.
 * Pure — no SQL, no JPA, no Spring; a plain constructor so it's trivially unit-testable
 * (doc 42 §6.6).
 */
public final class AllocationPolicy {

	private final RedistributionPolicy redistribution = new RedistributionPolicy();

	/**
	 * @param percents default New/Weak/Review split (e.g. 60/25/15)
	 * @param backlogPercents override used when {@code dueReviewCount > capacity} (doc 42
	 * §6.4) — e.g. 5/25/70, review-heavy so a returning learner isn't buried under new words
	 * on top of a two-week backlog
	 */
	public BucketTargets allocate(int capacity, Percents percents, Percents backlogPercents,
			int dueReviewCount, int weakCandidateCount, int newCandidateCount) {
		Percents effective = dueReviewCount > capacity ? backlogPercents : percents;
		BucketTargets desired = split(capacity, effective);
		return redistribution.redistribute(desired, dueReviewCount, weakCandidateCount,
				newCandidateCount);
	}

	/**
	 * Largest-remainder proportional split so the three targets sum to exactly
	 * {@code capacity} (a plain {@code floor} per bucket would silently drop up to two slots
	 * to rounding). Ties in the remainder go to review first, then weak, then new — the same
	 * review &gt; weak &gt; new priority used everywhere else in the planner.
	 */
	private BucketTargets split(int capacity, Percents percents) {
		int newFloor = capacity * percents.newPercent() / 100;
		int weakFloor = capacity * percents.weakPercent() / 100;
		int reviewFloor = capacity * percents.reviewPercent() / 100;

		int remainder = capacity - (newFloor + weakFloor + reviewFloor);
		record Share(String bucket, double fraction) {
		}
		List<Share> shares = List.of(
				new Share("review", fractional(capacity, percents.reviewPercent())),
				new Share("weak", fractional(capacity, percents.weakPercent())),
				new Share("new", fractional(capacity, percents.newPercent())));
		List<Share> ordered = shares.stream()
				.sorted(Comparator.comparingDouble(Share::fraction).reversed())
				.toList();

		for (int i = 0; i < remainder; i++) {
			switch (ordered.get(i % ordered.size()).bucket()) {
				case "review" -> reviewFloor++;
				case "weak" -> weakFloor++;
				default -> newFloor++;
			}
		}
		return new BucketTargets(newFloor, weakFloor, reviewFloor);
	}

	private double fractional(int capacity, int percent) {
		double exact = capacity * percent / 100.0;
		return exact - Math.floor(exact);
	}
}
