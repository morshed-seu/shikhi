package com.shikhi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A selectable option for MCQ/MATCH. {@code correct} is server-side only. */
@Entity
@Table(name = "exercise_options")
public class ExerciseOption {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "exercise_id", nullable = false)
	private UUID exerciseId;

	@Column(name = "text_en", nullable = false)
	private String textEn;

	@Column(name = "text_bn", nullable = false)
	private String textBn;

	@Column(name = "is_correct", nullable = false)
	private boolean correct;

	@Column(nullable = false)
	private int ordinal;

	protected ExerciseOption() {
		// for JPA
	}

	public ExerciseOption(UUID exerciseId, String textEn, String textBn, boolean correct,
			int ordinal) {
		this.exerciseId = exerciseId;
		this.textEn = textEn;
		this.textBn = textBn;
		this.correct = correct;
		this.ordinal = ordinal;
	}

	public UUID getId() {
		return id;
	}

	public UUID getExerciseId() {
		return exerciseId;
	}

	public String getTextEn() {
		return textEn;
	}

	public String getTextBn() {
		return textBn;
	}

	public boolean isCorrect() {
		return correct;
	}

	public int getOrdinal() {
		return ordinal;
	}
}
