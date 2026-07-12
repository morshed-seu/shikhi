package com.shikhi.practice.policy;

/**
 * Caps {@link AllocationPolicy}'s percentage-derived targets to what's actually available and
 * redistributes any shortfall to the next bucket in priority order — doc 42 §6.3: "review →
 * weak → new, because ignoring due reviews causes forgetting, ignoring weak words causes
 * frustration, and delaying new words merely slows progress."
 *
 * <p>Three passes, each only ever donating a bucket's unmet slots forward (never inventing
 * items a bucket doesn't have):
 * <ol>
 *   <li>a review shortfall splits 70/30 to weak/new (doc 42 §6.3 example);</li>
 *   <li>a (now possibly larger) weak shortfall donates entirely to new — weak has no lower
 *       priority bucket left to protect once review is already satisfied;</li>
 *   <li>a new shortfall — the common case once a learner has exhausted unseen vocabulary in a
 *       band — flows back up to weak first, then review, since both outrank new and may still
 *       have unused candidates.</li>
 * </ol>
 * If every bucket is short, the plan is simply smaller than the daily capacity; this class
 * never fabricates candidates.
 */
public final class RedistributionPolicy {

	public BucketTargets redistribute(BucketTargets desired, int dueReviewCount,
			int weakCandidateCount, int newCandidateCount) {
		int weakTarget = desired.weakTarget();
		int newTarget = desired.newTarget();

		int reviewSelected = Math.min(desired.reviewTarget(), dueReviewCount);
		int reviewShortfall = desired.reviewTarget() - reviewSelected;
		if (reviewShortfall > 0) {
			int toWeak = Math.round(reviewShortfall * 0.7f);
			int toNew = reviewShortfall - toWeak;
			weakTarget += toWeak;
			newTarget += toNew;
		}

		int weakSelected = Math.min(weakTarget, weakCandidateCount);
		int weakShortfall = weakTarget - weakSelected;
		if (weakShortfall > 0) {
			newTarget += weakShortfall;
		}

		int newSelected = Math.min(newTarget, newCandidateCount);
		int newShortfall = newTarget - newSelected;
		if (newShortfall > 0) {
			int weakHeadroom = Math.max(0, weakCandidateCount - weakSelected);
			int toWeak = Math.min(newShortfall, weakHeadroom);
			weakSelected += toWeak;

			int reviewHeadroom = Math.max(0, dueReviewCount - reviewSelected);
			int toReview = Math.min(newShortfall - toWeak, reviewHeadroom);
			reviewSelected += toReview;
			// Any remainder has nowhere left to go — every bucket is genuinely short.
		}

		return new BucketTargets(newSelected, weakSelected, reviewSelected);
	}
}
