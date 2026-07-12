package com.shikhi.practice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the hybrid daily learning planner (doc 43 §3/§5): graduation gate, interval
 * ladder, and bucket allocation. All percentages/thresholds/the ladder are configuration, not
 * code — starting hypotheses to be tuned against real learner outcomes (doc 43 §7), not values
 * to hardcode into policy classes.
 *
 * <p>{@code enabled} gates the plan-backed round composition landing in VE4 (feature flag +
 * automatic legacy fallback, doc 43 deviation #4); the graduation/ladder bookkeeping in
 * {@code WordProgressService} (VE2) runs unconditionally regardless of this flag, so a word's
 * review history is never lost while the planner is off. Default {@code false} in production
 * until VE4 is verified; {@code true} in dev only.
 */
@ConfigurationProperties(prefix = "shikhi.practice.planner")
public class PlannerProperties {

	/** Master switch for plan-backed round composition (VE4). */
	private boolean enabled = false;

	/** Total words served per learner per day once the planner is active. */
	private int dailyCapacity = 100;

	/** Share of the daily capacity drawn from unseen words. */
	private int newPercent = 60;

	/** Share of the daily capacity drawn from the computed weak bucket. */
	private int weakPercent = 25;

	/** Share of the daily capacity drawn from words due on the review ladder. */
	private int reviewPercent = 15;

	/** Minimum {@code masteryScore} for a word to graduate onto the review ladder. */
	private int graduationMastery = 3;

	/** Minimum {@code timesCorrect} for a word to graduate onto the review ladder. */
	private int graduationTimesCorrect = 2;

	/** Minimum {@code timesSeen} for a word to graduate onto the review ladder. */
	private int graduationTimesSeen = 3;

	/** Day-counts for {@code ReviewProgress.reviewStage} 0..N (doc 42 §7.2). */
	private List<Integer> reviewIntervalsDays =
			List.of(0, 1, 3, 7, 14, 30, 60, 120, 180, 365);

	/** Weak bucket eligibility (doc 42 §5): masteryScore at or below this is "weak". */
	private int weakMasteryThreshold = 2;

	/** New-word CEFR split (doc 42 §6.5): share drawn from the learner's current band. */
	private int newCurrentBandPercent = 90;

	/** Weak-word CEFR split (doc 42 §6.5): share drawn from the learner's current band. */
	private int weakCurrentBandPercent = 60;

	/** {@link com.shikhi.practice.policy.BucketMixer} cap on same-bucket runs (doc 42 §8.3). */
	private int maxConsecutiveBucket = 3;

	/** Backlog protection (doc 42 §6.4): review share once due reviews exceed capacity. */
	private int backlogReviewPercent = 70;

	/** Backlog protection (doc 42 §6.4): weak share once due reviews exceed capacity. */
	private int backlogWeakPercent = 25;

	/** Backlog protection (doc 42 §6.4): new share once due reviews exceed capacity. */
	private int backlogNewPercent = 5;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public int getDailyCapacity() {
		return dailyCapacity;
	}

	public void setDailyCapacity(int dailyCapacity) {
		this.dailyCapacity = dailyCapacity;
	}

	public int getNewPercent() {
		return newPercent;
	}

	public void setNewPercent(int newPercent) {
		this.newPercent = newPercent;
	}

	public int getWeakPercent() {
		return weakPercent;
	}

	public void setWeakPercent(int weakPercent) {
		this.weakPercent = weakPercent;
	}

	public int getReviewPercent() {
		return reviewPercent;
	}

	public void setReviewPercent(int reviewPercent) {
		this.reviewPercent = reviewPercent;
	}

	public int getGraduationMastery() {
		return graduationMastery;
	}

	public void setGraduationMastery(int graduationMastery) {
		this.graduationMastery = graduationMastery;
	}

	public int getGraduationTimesCorrect() {
		return graduationTimesCorrect;
	}

	public void setGraduationTimesCorrect(int graduationTimesCorrect) {
		this.graduationTimesCorrect = graduationTimesCorrect;
	}

	public int getGraduationTimesSeen() {
		return graduationTimesSeen;
	}

	public void setGraduationTimesSeen(int graduationTimesSeen) {
		this.graduationTimesSeen = graduationTimesSeen;
	}

	public List<Integer> getReviewIntervalsDays() {
		return reviewIntervalsDays;
	}

	public void setReviewIntervalsDays(List<Integer> reviewIntervalsDays) {
		this.reviewIntervalsDays = reviewIntervalsDays;
	}

	public int getWeakMasteryThreshold() {
		return weakMasteryThreshold;
	}

	public void setWeakMasteryThreshold(int weakMasteryThreshold) {
		this.weakMasteryThreshold = weakMasteryThreshold;
	}

	public int getNewCurrentBandPercent() {
		return newCurrentBandPercent;
	}

	public void setNewCurrentBandPercent(int newCurrentBandPercent) {
		this.newCurrentBandPercent = newCurrentBandPercent;
	}

	public int getWeakCurrentBandPercent() {
		return weakCurrentBandPercent;
	}

	public void setWeakCurrentBandPercent(int weakCurrentBandPercent) {
		this.weakCurrentBandPercent = weakCurrentBandPercent;
	}

	public int getMaxConsecutiveBucket() {
		return maxConsecutiveBucket;
	}

	public void setMaxConsecutiveBucket(int maxConsecutiveBucket) {
		this.maxConsecutiveBucket = maxConsecutiveBucket;
	}

	public int getBacklogReviewPercent() {
		return backlogReviewPercent;
	}

	public void setBacklogReviewPercent(int backlogReviewPercent) {
		this.backlogReviewPercent = backlogReviewPercent;
	}

	public int getBacklogWeakPercent() {
		return backlogWeakPercent;
	}

	public void setBacklogWeakPercent(int backlogWeakPercent) {
		this.backlogWeakPercent = backlogWeakPercent;
	}

	public int getBacklogNewPercent() {
		return backlogNewPercent;
	}

	public void setBacklogNewPercent(int backlogNewPercent) {
		this.backlogNewPercent = backlogNewPercent;
	}
}
