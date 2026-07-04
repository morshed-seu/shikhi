package com.shikhi.practice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One generated practice exercise (E12). {@code payload} is the learner-visible config
 * (options/tokens — no correctness flags); {@code answerKey} is server-only (the correct
 * option id, or the accepted answers) and is never serialized to clients — the same
 * "correctness never leaves the server" rule as curriculum exercises.
 */
@Entity
@Table(name = "practice_exercises")
public class PracticeExercise {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "session_id", nullable = false)
	private UUID sessionId;

	@Column(nullable = false)
	private int round;

	@Column(nullable = false)
	private int ordinal;

	@Column(name = "vocabulary_id", nullable = false)
	private UUID vocabularyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PracticeExerciseType type;

	@Column(name = "prompt_en", nullable = false)
	private String promptEn;

	@Column(name = "prompt_bn", nullable = false)
	private String promptBn;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Map<String, Object> payload;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "answer_key", nullable = false)
	private Map<String, Object> answerKey;

	@Column(name = "answered_correct")
	private Boolean answeredCorrect;

	protected PracticeExercise() {
		// for JPA
	}

	public PracticeExercise(UUID sessionId, int round, int ordinal, UUID vocabularyId,
			PracticeExerciseType type, String promptEn, String promptBn,
			Map<String, Object> payload, Map<String, Object> answerKey) {
		this.sessionId = sessionId;
		this.round = round;
		this.ordinal = ordinal;
		this.vocabularyId = vocabularyId;
		this.type = type;
		this.promptEn = promptEn;
		this.promptBn = promptBn;
		this.payload = payload;
		this.answerKey = answerKey;
	}

	public void markAnswered(boolean correct) {
		this.answeredCorrect = correct;
	}

	public boolean isAnswered() {
		return answeredCorrect != null;
	}

	public UUID getId() {
		return id;
	}

	public UUID getSessionId() {
		return sessionId;
	}

	public int getRound() {
		return round;
	}

	public int getOrdinal() {
		return ordinal;
	}

	public UUID getVocabularyId() {
		return vocabularyId;
	}

	public PracticeExerciseType getType() {
		return type;
	}

	public String getPromptEn() {
		return promptEn;
	}

	public String getPromptBn() {
		return promptBn;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}

	public Map<String, Object> getAnswerKey() {
		return answerKey;
	}

	public Boolean getAnsweredCorrect() {
		return answeredCorrect;
	}
}
