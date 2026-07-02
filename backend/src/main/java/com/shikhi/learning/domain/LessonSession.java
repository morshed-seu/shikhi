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
 * One attempt at a lesson, pinned to the content version it started on (F4). Tracks a running
 * score of correct answers. Since M4, hearts live on the learner (user_stats) and are spent
 * across lessons; {@code heartsRemaining} here is just the snapshot captured at session start.
 */
@Entity
@Table(name = "lesson_sessions")
public class LessonSession {

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
	private int heartsRemaining;

	@Column(nullable = false)
	private int score;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt = Instant.now();

	@Column(name = "completed_at")
	private Instant completedAt;

	protected LessonSession() {
		// for JPA
	}

	public LessonSession(UUID userId, UUID lessonId, UUID contentVersionId, int initialHearts) {
		this.userId = userId;
		this.lessonId = lessonId;
		this.contentVersionId = contentVersionId;
		this.heartsRemaining = initialHearts;
	}

	/** Count a correct answer toward the session score (hearts are handled by the progress module). */
	public void recordAnswer(boolean correct) {
		if (correct) {
			score++;
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
