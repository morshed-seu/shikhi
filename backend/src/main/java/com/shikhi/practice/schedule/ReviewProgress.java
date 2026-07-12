package com.shikhi.practice.schedule;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One learner's position on the word-level spaced-repetition ladder (doc 43 §3/§5). A row
 * exists only once a word has GRADUATED out of {@code practice_word_progress} — the
 * graduation gate (masteryScore/timesCorrect/timesSeen thresholds, {@code PlannerProperties})
 * decides when that happens, in {@code WordProgressService}. From then on, {@code reviewStage}
 * indexes a {@link WordReviewScheduler}-defined interval ladder and {@code dueAt} drives the
 * REVIEW bucket of the daily plan (VE3).
 *
 * <p>Transitions (doc 43 §3, deviation #7): a correct answer promotes the ladder only when the
 * word was actually due — non-due appearances (served via the New/Weak buckets in a unified
 * session) must not inflate future intervals, and a late review still promotes normally
 * (lateness is never punished). A wrong answer always demotes, unconditionally, and feeds
 * {@code failureStreak} into the weak-word priority once the word falls out of review.
 */
@Entity
@Table(name = "review_progress")
public class ReviewProgress {

	@Embeddable
	public record Key(@Column(name = "user_id") UUID userId,
			@Column(name = "vocabulary_id") UUID vocabularyId) implements Serializable {

		public Key {
			Objects.requireNonNull(userId);
			Objects.requireNonNull(vocabularyId);
		}
	}

	@EmbeddedId
	private Key key;

	@Column(name = "review_stage", nullable = false)
	private int reviewStage;

	@Column(name = "due_at", nullable = false)
	private Instant dueAt;

	@Column(name = "last_reviewed_at")
	private Instant lastReviewedAt;

	@Column(name = "review_count", nullable = false)
	private int reviewCount;

	@Column(name = "successful_reviews", nullable = false)
	private int successfulReviews;

	@Column(name = "failed_reviews", nullable = false)
	private int failedReviews;

	@Column(name = "failure_streak", nullable = false)
	private int failureStreak;

	@Column(name = "last_failure_at")
	private Instant lastFailureAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected ReviewProgress() {
		// for JPA
	}

	/** Graduation: a word enters the ladder at {@code initialStage}, due at {@code dueAt}. */
	public ReviewProgress(UUID userId, UUID vocabularyId, int initialStage, Instant dueAt) {
		this.key = new Key(userId, vocabularyId);
		this.reviewStage = initialStage;
		this.dueAt = Objects.requireNonNull(dueAt);
	}

	/** Correct answer while due: advance one stage and push the next due date out. */
	public void promote(Instant now, Duration nextInterval) {
		reviewStage++;
		dueAt = now.plus(nextInterval);
		lastReviewedAt = now;
		reviewCount++;
		successfulReviews++;
		failureStreak = 0;
		updatedAt = now;
	}

	/** Wrong answer: drop two stages (floor 0) and reschedule sooner. */
	public void demote(Instant now, Duration nextInterval) {
		reviewStage = Math.max(0, reviewStage - 2);
		dueAt = now.plus(nextInterval);
		reviewCount++;
		failedReviews++;
		failureStreak++;
		lastFailureAt = now;
		updatedAt = now;
	}

	public boolean isDue(Instant now) {
		return !dueAt.isAfter(now);
	}

	public Key getKey() {
		return key;
	}

	public int getReviewStage() {
		return reviewStage;
	}

	public Instant getDueAt() {
		return dueAt;
	}

	public Instant getLastReviewedAt() {
		return lastReviewedAt;
	}

	public int getReviewCount() {
		return reviewCount;
	}

	public int getSuccessfulReviews() {
		return successfulReviews;
	}

	public int getFailedReviews() {
		return failedReviews;
	}

	public int getFailureStreak() {
		return failureStreak;
	}

	public Instant getLastFailureAt() {
		return lastFailureAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
