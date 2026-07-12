package com.shikhi.practice.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Doc 42 §6.2-§6.4: percentage split, redistribution shortfalls (review -> weak -> new), and
 * backlog protection. No DB, no Spring — {@link AllocationPolicy} is pure.
 */
class AllocationPolicyTest {

	private final AllocationPolicy policy = new AllocationPolicy();
	private final Percents percents = new Percents(60, 25, 15);
	private final Percents backlogPercents = new Percents(5, 25, 70);

	@Test
	void splitsExactlyByPercentWhenEverythingIsAbundant() {
		// dueReviewCount must stay <= capacity here or backlog protection kicks in instead —
		// that's a separate scenario, covered below.
		BucketTargets targets = policy.allocate(100, percents, backlogPercents, 15, 1000, 1000);

		assertThat(targets.newTarget()).isEqualTo(60);
		assertThat(targets.weakTarget()).isEqualTo(25);
		assertThat(targets.reviewTarget()).isEqualTo(15);
	}

	@Test
	void percentSplitSumsExactlyToCapacityDespiteRounding() {
		// 33/33/34 of 100 isn't evenly divisible by thirds; the largest-remainder method must
		// still land on exactly 100, never 99 or 101.
		Percents oddPercents = new Percents(34, 33, 33);
		BucketTargets targets = policy.allocate(97, oddPercents, backlogPercents, 50, 1000, 1000);

		assertThat(targets.newTarget() + targets.weakTarget() + targets.reviewTarget())
				.isEqualTo(97);
	}

	@Test
	void shortReviewQueueRedistributes70PercentToWeakAnd30PercentToNew() {
		// capacity 100 -> review target 15, only 3 due -> shortfall 12 -> weak += 8, new += 4.
		BucketTargets targets = policy.allocate(100, percents, backlogPercents, 3, 1000, 1000);

		assertThat(targets.reviewTarget()).isEqualTo(3);
		assertThat(targets.weakTarget()).isEqualTo(25 + 8);
		assertThat(targets.newTarget()).isEqualTo(60 + 4);
	}

	@Test
	void shortWeakPoolDonatesEntirelyToNew() {
		// weak target 25, only 10 weak candidates available -> shortfall 15 goes to new.
		// dueReviewCount = 100 (== capacity) stays under the backlog threshold.
		BucketTargets targets = policy.allocate(100, percents, backlogPercents, 100, 10, 1000);

		assertThat(targets.weakTarget()).isEqualTo(10);
		assertThat(targets.newTarget()).isEqualTo(60 + 15);
		assertThat(targets.reviewTarget()).isEqualTo(15);
	}

	@Test
	void shortNewPoolDonatesToWeakThenReview() {
		// new target 60, only 20 new candidates -> shortfall 40. Weak has headroom for up to
		// (1000 - 25) slots, so weak absorbs the whole 40 before review sees anything.
		BucketTargets targets = policy.allocate(100, percents, backlogPercents, 100, 1000, 20);

		assertThat(targets.newTarget()).isEqualTo(20);
		assertThat(targets.weakTarget()).isEqualTo(25 + 40);
		assertThat(targets.reviewTarget()).isEqualTo(15);
	}

	@Test
	void shortNewPoolOverflowsToReviewOnceWeakIsAlsoCapped() {
		// new target 60, only 5 new candidates -> shortfall 55. Weak candidates capped at 30
		// (target 25, so only 5 headroom) -> remaining 50 shortfall flows to review, which has
		// plenty of headroom (100 due, only 15 selected so far).
		BucketTargets targets = policy.allocate(100, percents, backlogPercents, 100, 30, 5);

		assertThat(targets.newTarget()).isEqualTo(5);
		assertThat(targets.weakTarget()).isEqualTo(30); // 25 + 5 headroom, fully absorbed
		assertThat(targets.reviewTarget()).isEqualTo(15 + 50);
	}

	@Test
	void everyBucketShortMeansASmallerPlanNotInventedItems() {
		BucketTargets targets = policy.allocate(100, percents, backlogPercents, 2, 3, 4);

		assertThat(targets.reviewTarget()).isEqualTo(2);
		assertThat(targets.weakTarget()).isEqualTo(3);
		assertThat(targets.newTarget()).isEqualTo(4);
	}

	@Test
	void backlogProtectionDoesNotTriggerWhenDueCountEqualsCapacity() {
		BucketTargets targets = policy.allocate(100, percents, backlogPercents, 100, 1000, 1000);

		assertThat(targets.reviewTarget()).isEqualTo(15); // normal split, not backlog
	}

	@Test
	void backlogProtectionTriggersOncePastCapacity() {
		BucketTargets targets = policy.allocate(100, percents, backlogPercents, 101, 1000, 1000);

		assertThat(targets.reviewTarget()).isEqualTo(70);
		assertThat(targets.weakTarget()).isEqualTo(25);
		assertThat(targets.newTarget()).isEqualTo(5);
	}

	@Test
	void backlogProtectionStillRespectsCandidateAvailability() {
		// Even under backlog protection, review can't select more than what's actually due.
		BucketTargets targets = policy.allocate(100, percents, backlogPercents, 200, 1000, 1000);

		assertThat(targets.reviewTarget()).isEqualTo(70);
	}
}
