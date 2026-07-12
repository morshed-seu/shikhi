package com.shikhi.practice.policy;

/**
 * Adapts the New/Weak percentage split to a learner's rolling accuracy (doc 42 §6.4, doc 43
 * deviation #10 — the last piece of the adaptive core, shipped once the deterministic planner
 * is stable). REVIEW never moves: it is driven by what's actually due, not by how well the
 * learner is doing elsewhere. Pure — no SQL, no JPA, no Spring, so it's trivially unit-testable
 * (doc 42 §6.6); {@link com.shikhi.practice.plan.DailyPlanService} computes the rolling accuracy
 * and calls this <em>before</em> {@link AllocationPolicy#allocate}, so backlog protection (which
 * runs inside {@code allocate}) still has the final say over the split it's handed.
 *
 * <p>Every tier boundary below (and the two point-shift magnitudes) is a starting hypothesis to
 * be tuned against real learner outcomes, not a settled constant (doc 42 §15) — expect these
 * numbers to change once retention/completion data exists.
 */
public final class AdaptiveAllocationPolicy {

	/** Below this many answers in the rolling window, there isn't enough signal to adapt. */
	public static final int MIN_SAMPLE_SIZE = 20;

	/** At/above this accuracy the learner is cruising: introduce more new material. */
	public static final double CRUISING_ACCURACY = 0.95;

	/** At/above this accuracy (below {@link #CRUISING_ACCURACY}) the pace is left unchanged. */
	public static final double STABLE_ACCURACY = 0.90;

	/** At/above this accuracy (below {@link #STABLE_ACCURACY}) new intake is trimmed slightly. */
	public static final double SLOWING_ACCURACY = 0.75;

	/** At/above this accuracy (below {@link #SLOWING_ACCURACY}) new intake is cut sharply. */
	public static final double STRUGGLING_ACCURACY = 0.60;

	/** Points moved between NEW and WEAK when {@link #CRUISING_ACCURACY} applies. */
	private static final int CRUISING_SHIFT = 10;

	/** Points moved between NEW and WEAK when {@link #SLOWING_ACCURACY} applies. */
	private static final int SLOWING_SHIFT = 10;

	/** Points moved between NEW and WEAK when {@link #STRUGGLING_ACCURACY} applies. */
	private static final int STRUGGLING_SHIFT = 30;

	/** NEW percentage floor in repair mode (below {@link #STRUGGLING_ACCURACY}). */
	private static final int REPAIR_NEW_PERCENT = 5;

	/**
	 * @param base the configured New/Weak/Review split before adaptation
	 * @param rollingAccuracy fraction correct (0.0-1.0) over the learner's rolling window
	 * @param sampleSize number of answers the accuracy was computed from
	 * @return an adjusted split; REVIEW is always {@code base.reviewPercent()} unchanged, and
	 * NEW + WEAK always sum to {@code base.newPercent() + base.weakPercent()} — points only ever
	 * move between those two buckets, never invented or dropped
	 */
	public Percents adjust(Percents base, double rollingAccuracy, int sampleSize) {
		if (sampleSize < MIN_SAMPLE_SIZE) {
			// Cold start (doc 42 §6.4 caveat): too little data this week to trust an adjustment.
			return base;
		}
		if (rollingAccuracy >= CRUISING_ACCURACY) {
			return shiftTowardNew(base, CRUISING_SHIFT);
		}
		if (rollingAccuracy >= STABLE_ACCURACY) {
			return base;
		}
		if (rollingAccuracy >= SLOWING_ACCURACY) {
			return shiftTowardNew(base, -SLOWING_SHIFT);
		}
		if (rollingAccuracy >= STRUGGLING_ACCURACY) {
			return shiftTowardNew(base, -STRUGGLING_SHIFT);
		}
		return repairMode(base);
	}

	/**
	 * Moves {@code points} from WEAK into NEW ({@code points > 0}) or from NEW into WEAK
	 * ({@code points < 0}), clamped to what the donor bucket actually has so neither bucket ever
	 * goes negative — the clamp is what keeps NEW + WEAK exactly equal to the pre-adjustment sum
	 * even at the extremes.
	 */
	private Percents shiftTowardNew(Percents base, int points) {
		if (points > 0) {
			int move = Math.min(points, base.weakPercent());
			return new Percents(base.newPercent() + move, base.weakPercent() - move,
					base.reviewPercent());
		}
		if (points < 0) {
			int move = Math.min(-points, base.newPercent());
			return new Percents(base.newPercent() - move, base.weakPercent() + move,
					base.reviewPercent());
		}
		return base;
	}

	/**
	 * Repair mode (doc 42 §6.4: "new-word introduction nearly suspended"): NEW falls to
	 * {@link #REPAIR_NEW_PERCENT} (or stays put if it was already at or below that floor), and
	 * WEAK absorbs whatever NEW gave up.
	 */
	private Percents repairMode(Percents base) {
		int newTarget = Math.min(REPAIR_NEW_PERCENT, base.newPercent());
		int absorbed = base.newPercent() - newTarget;
		return new Percents(newTarget, base.weakPercent() + absorbed, base.reviewPercent());
	}
}
