package com.shikhi.learning.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt at a lesson, pinned to the content version it started on (F4). Tracks hearts
 * (lives) and a running score of correct answers. XP/streak aggregation across sessions is
 * M4 — this entity only owns a single play-through's state.
 */
@Entity
@Table(name = "lesson_sessions")
public class LessonSession {

	/** Hearts a learner starts a lesson with (E4 lesson player). */
	public static final int STARTING_HEARTS = 5;

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "lesson_id", nullable = false)
	private UUID lessonId;

	@Column(name = "content_version_id", nullable = false)
	private UUID contentVersionId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SessionStatus status = SessionStatus.IN_PROGRESS;

	@Column(name = "hearts_remaining", nullable = false)
	private int heartsRemaining = STARTING_HEARTS;

	@Column(nullable = false)
	private int score;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt = Instant.now();

	@Column(name = "completed_at")
	private Instant completedAt;

	protected LessonSession() {
		// for JPA
	}

	public LessonSession(UUID userId, UUID lessonId, UUID contentVersionId) {
		this.userId = userId;
		this.lessonId = lessonId;
		this.contentVersionId = contentVersionId;
	}

	/** Apply a graded answer: a correct answer scores; a wrong one costs a heart (floored at 0). */
	public void recordAnswer(boolean correct) {
		if (correct) {
			score++;
		}
		else if (heartsRemaining > 0) {
			heartsRemaining--;
		}
	}

	public void complete() {
		this.status = SessionStatus.COMPLETED;
		this.completedAt = Instant.now();
	}

	public boolean isCompleted() {
		return status == SessionStatus.COMPLETED;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getLessonId() {
		return lessonId;
	}

	public UUID getContentVersionId() {
		return contentVersionId;
	}

	public SessionStatus getStatus() {
		return status;
	}

	public int getHeartsRemaining() {
		return heartsRemaining;
	}

	public int getScore() {
		return score;
	}
}
