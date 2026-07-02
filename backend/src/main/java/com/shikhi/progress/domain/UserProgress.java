package com.shikhi.progress.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A learner's progress on one lesson within a content version (LLD §3.3). Unique per
 * (user, lesson, version) so completion counts once. Version-pinned so republishing content
 * does not silently reset or move a learner's history.
 */
@Entity
@Table(name = "user_progress")
public class UserProgress {

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
	private ProgressStatus status = ProgressStatus.NOT_STARTED;

	@Column(name = "best_score", nullable = false)
	private int bestScore;

	@Column(name = "completed_at")
	private Instant completedAt;

	protected UserProgress() {
		// for JPA
	}

	public UserProgress(UUID userId, UUID lessonId, UUID contentVersionId) {
		this.userId = userId;
		this.lessonId = lessonId;
		this.contentVersionId = contentVersionId;
	}

	/** Mark this lesson completed, keeping the learner's best score. */
	public void complete(int score) {
		this.status = ProgressStatus.COMPLETED;
		this.bestScore = Math.max(this.bestScore, score);
		this.completedAt = Instant.now();
	}

	public UUID getLessonId() {
		return lessonId;
	}

	public ProgressStatus getStatus() {
		return status;
	}

	public int getBestScore() {
		return bestScore;
	}
}
