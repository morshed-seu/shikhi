package com.shikhi.practice.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Doc 42 §6.4: rolling-accuracy tiers move points between NEW and WEAK; REVIEW never moves.
 * No DB, no Spring — {@link AdaptiveAllocationPolicy} is pure.
 */
class AdaptiveAllocationPolicyTest {

	private final AdaptiveAllocationPolicy policy = new AdaptiveAllocationPolicy();
	private final Percents base = new Percents(60, 25, 15);

	@Test
	void coldStartBelowMinSampleSizeLeavesBaseUnchanged() {
		Percents adjusted = policy.adjust(base, 0.30, AdaptiveAllocationPolicy.MIN_SAMPLE_SIZE - 1);

		assertThat(adjusted).isEqualTo(base);
	}

	@Test
	void coldStartBoundaryAtMinSampleSizeAlreadyAdapts() {
		// sampleSize == MIN_SAMPLE_SIZE is enough data — cold start is strictly "<", not "<=".
		Percents adjusted = policy.adjust(base, 0.99, AdaptiveAllocationPolicy.MIN_SAMPLE_SIZE);

		assertThat(adjusted).isEqualTo(new Percents(70, 15, 15));
	}

	@Test
	void cruisingAtOrAbove95PercentShiftsTenPointsFromWeakToNew() {
		Percents adjusted = policy.adjust(base, 0.95, 100);

		assertThat(adjusted).isEqualTo(new Percents(70, 15, 15));
	}

	@Test
	void justBelowCruisingBoundaryIsStableInstead() {
		Percents adjusted = policy.adjust(base, 0.9499, 100);

		assertThat(adjusted).isEqualTo(base);
	}

	@Test
	void stableAtOrAbove90PercentLeavesBaseUnchanged() {
		Percents adjusted = policy.adjust(base, 0.90, 100);

		assertThat(adjusted).isEqualTo(base);
	}

	@Test
	void justBelowStableBoundaryIsSlowingInstead() {
		Percents adjusted = policy.adjust(base, 0.8999, 100);

		assertThat(adjusted).isEqualTo(new Percents(50, 35, 15));
	}

	@Test
	void slowingAtOrAbove75PercentShiftsTenPointsFromNewToWeak() {
		Percents adjusted = policy.adjust(base, 0.75, 100);

		assertThat(adjusted).isEqualTo(new Percents(50, 35, 15));
	}

	@Test
	void justBelowSlowingBoundaryIsStrugglingInstead() {
		Percents adjusted = policy.adjust(base, 0.7499, 100);

		assertThat(adjusted).isEqualTo(new Percents(30, 55, 15));
	}

	@Test
	void strugglingAtOrAbove60PercentShiftsThirtyPointsFromNewToWeak() {
		Percents adjusted = policy.adjust(base, 0.60, 100);

		assertThat(adjusted).isEqualTo(new Percents(30, 55, 15));
	}

	@Test
	void justBelowStrugglingBoundaryEntersRepairMode() {
		Percents adjusted = policy.adjust(base, 0.5999, 100);

		assertThat(adjusted).isEqualTo(new Percents(5, 80, 15));
	}

	@Test
	void repairModeWellBelow60PercentDropsNewToFivePercent() {
		Percents adjusted = policy.adjust(base, 0.10, 100);

		assertThat(adjusted).isEqualTo(new Percents(5, 80, 15));
	}

	@Test
	void reviewNeverMovesRegardlessOfTier() {
		assertThat(policy.adjust(base, 0.99, 100).reviewPercent()).isEqualTo(15);
		assertThat(policy.adjust(base, 0.10, 100).reviewPercent()).isEqualTo(15);
		assertThat(policy.adjust(base, 0.80, 100).reviewPercent()).isEqualTo(15);
	}

	@Test
	void newPlusWeakAlwaysSumsToBaseNewPlusWeakAcrossEveryTier() {
		int baseSum = base.newPercent() + base.weakPercent();
		double[] accuracies = { 0.99, 0.95, 0.92, 0.90, 0.85, 0.75, 0.65, 0.60, 0.30, 0.0 };
		for (double accuracy : accuracies) {
			Percents adjusted = policy.adjust(base, accuracy, 100);
			assertThat(adjusted.newPercent() + adjusted.weakPercent())
					.as("accuracy=%s", accuracy).isEqualTo(baseSum);
			assertThat(adjusted.newPercent()).as("accuracy=%s newPercent", accuracy)
					.isGreaterThanOrEqualTo(0);
			assertThat(adjusted.weakPercent()).as("accuracy=%s weakPercent", accuracy)
					.isGreaterThanOrEqualTo(0);
		}
	}

	@Test
	void cruisingShiftClampsToAvailableWeakRatherThanGoingNegative() {
		// weakPercent only has 5 points to give; the +10 shift must clamp, not overshoot to -5.
		Percents thinWeak = new Percents(80, 5, 15);
		Percents adjusted = policy.adjust(thinWeak, 0.99, 100);

		assertThat(adjusted).isEqualTo(new Percents(85, 0, 15));
	}

	@Test
	void slowingShiftClampsToAvailableNewRatherThanGoingNegative() {
		// newPercent only has 6 points to give; the -10 shift must clamp, not overshoot to -4.
		Percents thinNew = new Percents(6, 79, 15);
		Percents adjusted = policy.adjust(thinNew, 0.80, 100);

		assertThat(adjusted).isEqualTo(new Percents(0, 85, 15));
	}

	@Test
	void strugglingShiftClampsToAvailableNewRatherThanGoingNegative() {
		Percents thinNew = new Percents(10, 75, 15);
		Percents adjusted = policy.adjust(thinNew, 0.65, 100);

		assertThat(adjusted).isEqualTo(new Percents(0, 85, 15));
	}

	@Test
	void repairModeWhenNewAlreadyBelowFloorLeavesItUntouched() {
		// newPercent is already 3, below the 5-point repair floor — nothing left to drop.
		Percents thinNew = new Percents(3, 82, 15);
		Percents adjusted = policy.adjust(thinNew, 0.10, 100);

		assertThat(adjusted).isEqualTo(thinNew);
	}
}
