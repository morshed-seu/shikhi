package com.shikhi.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One learner's spaced-repetition schedule for one exercise (LLD §3.4). Enters box 1 when
 * missed in a lesson; correct recalls promote it (longer interval), a miss demotes it back to
 * box 1. {@code dueAt} drives the review queue.
 */
@Entity
@Table(name = "review_items")
public class ReviewItem {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "exercise_id", nullable = false)
	private UUID exerciseId;

	@Column(name = "box_level", nullable = false)
	private int boxLevel = ReviewScheduler.MIN_BOX;

	@Column(name = "due_at", nullable = false)
	private Instant dueAt;

	@Column(name = "last_result")
	private Boolean lastResult;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected ReviewItem() {
		// for JPA
	}

	public ReviewItem(UUID userId, UUID exerciseId) {
		this.userId = userId;
		this.exerciseId = exerciseId;
		missed();
	}

	/** Missed in a lesson (or a review): back to box 1, due again soon. */
	public void missed() {
		this.boxLevel = ReviewScheduler.MIN_BOX;
		this.lastResult = Boolean.FALSE;
		reschedule();
	}

	/** Recalled correctly in a review: promote one box, schedule further out. */
	public void recalled() {
		this.boxLevel = ReviewScheduler.clampBox(boxLevel + 1);
		this.lastResult = Boolean.TRUE;
		reschedule();
	}

	private void reschedule() {
		Instant now = Instant.now();
		this.dueAt = now.plus(ReviewScheduler.interval(boxLevel));
		this.updatedAt = now;
	}

	public UUID getExerciseId() {
		return exerciseId;
	}

	public int getBoxLevel() {
		return boxLevel;
	}

	public Instant getDueAt() {
		return dueAt;
	}
}
