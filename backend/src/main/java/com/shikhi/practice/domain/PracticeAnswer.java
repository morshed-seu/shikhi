package com.shikhi.practice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A graded practice answer, keyed by {@code (user_id, idempotency_key)} so a client replay
 * returns the original verdict instead of grading (and charging a heart) twice — the same
 * replay-safety contract as {@code answer_submissions} (NFR-DI1).
 */
@Entity
@Table(name = "practice_answers")
public class PracticeAnswer {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "session_id", nullable = false)
	private UUID sessionId;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "exercise_id", nullable = false)
	private UUID exerciseId;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	@Column(nullable = false)
	private boolean correct;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt = Instant.now();

	protected PracticeAnswer() {
		// for JPA
	}

	public PracticeAnswer(UUID sessionId, UUID userId, UUID exerciseId, String idempotencyKey,
			boolean correct) {
		this.sessionId = sessionId;
		this.userId = userId;
		this.exerciseId = exerciseId;
		this.idempotencyKey = idempotencyKey;
		this.correct = correct;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public UUID getExerciseId() {
		return exerciseId;
	}

	public boolean isCorrect() {
		return correct;
	}
}
