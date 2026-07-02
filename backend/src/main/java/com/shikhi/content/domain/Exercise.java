package com.shikhi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A single exercise. Correctness (option flags / accepted answers) is modelled in sibling
 * tables and never serialized to learners — grading is server-side (M3).
 */
@Entity
@Table(name = "exercises")
public class Exercise {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "lesson_id", nullable = false)
	private UUID lessonId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ExerciseType type;

	@Column(nullable = false)
	private int ordinal;

	@Column(name = "prompt_en", nullable = false)
	private String promptEn;

	@Column(name = "prompt_bn", nullable = false)
	private String promptBn;

	@Column(name = "media_ref")
	private String mediaRef;

	protected Exercise() {
		// for JPA
	}

	public Exercise(UUID lessonId, ExerciseType type, int ordinal, String promptEn,
			String promptBn, String mediaRef) {
		this.lessonId = lessonId;
		this.type = type;
		this.ordinal = ordinal;
		this.promptEn = promptEn;
		this.promptBn = promptBn;
		this.mediaRef = mediaRef;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLessonId() {
		return lessonId;
	}

	public ExerciseType getType() {
		return type;
	}

	public int getOrdinal() {
		return ordinal;
	}

	public String getPromptEn() {
		return promptEn;
	}

	public String getPromptBn() {
		return promptBn;
	}

	public String getMediaRef() {
		return mediaRef;
	}
}
