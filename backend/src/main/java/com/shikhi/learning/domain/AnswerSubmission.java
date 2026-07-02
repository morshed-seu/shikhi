package com.shikhi.learning.domain;

import com.shikhi.content.web.Bilingual;
import com.shikhi.learning.grading.GradingVerdict;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A graded answer within a session. Keyed by {@code (user_id, idempotency_key)} so a client
 * replay returns the original verdict instead of grading (and charging a heart) twice. The
 * verdict's feedback is stored in both locales to reproduce it exactly on replay.
 */
@Entity
@Table(name = "answer_submissions")
public class AnswerSubmission {

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

	@Column(name = "matched_pattern_code")
	private String matchedPatternCode;

	@Column(name = "feedback_en", nullable = false)
	private String feedbackEn;

	@Column(name = "feedback_bn", nullable = false)
	private String feedbackBn;

	@Column(name = "submitted_at", nullable = false)
	private Instant submittedAt = Instant.now();

	protected AnswerSubmission() {
		// for JPA
	}

	public AnswerSubmission(UUID sessionId, UUID userId, UUID exerciseId, String idempotencyKey,
			GradingVerdict verdict) {
		this.sessionId = sessionId;
		this.userId = userId;
		this.exerciseId = exerciseId;
		this.idempotencyKey = idempotencyKey;
		this.correct = verdict.correct();
		this.matchedPatternCode = verdict.matchedPatternCode();
		this.feedbackEn = verdict.feedback().en();
		this.feedbackBn = verdict.feedback().bn();
	}

	/** Rebuild the original verdict for an idempotent replay. */
	public GradingVerdict toVerdict() {
		return new GradingVerdict(correct, new Bilingual(feedbackEn, feedbackBn),
				matchedPatternCode, GradingVerdict.SOURCE_RULE);
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public boolean isCorrect() {
		return correct;
	}
}
