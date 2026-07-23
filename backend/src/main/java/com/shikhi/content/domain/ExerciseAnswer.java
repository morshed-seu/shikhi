package com.shikhi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** An accepted answer for TYPE_TRANSLATION / FILL_BLANK. Server-side only (M3 grading). */
@Entity
@Table(name = "exercise_answers")
public class ExerciseAnswer {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "exercise_id", nullable = false)
	private UUID exerciseId;

	@Column(name = "accepted_answer", nullable = false)
	private String acceptedAnswer;

	@Column(name = "is_primary", nullable = false)
	private boolean primary;

	protected ExerciseAnswer() {
		// for JPA
	}

	public ExerciseAnswer(UUID exerciseId, String acceptedAnswer, boolean primary) {
		this.exerciseId = exerciseId;
		this.acceptedAnswer = acceptedAnswer;
		this.primary = primary;
	}

	public UUID getId() {
		return id;
	}

	public UUID getExerciseId() {
		return exerciseId;
	}

	public String getAcceptedAnswer() {
		return acceptedAnswer;
	}

	public boolean isPrimary() {
		return primary;
	}
}
