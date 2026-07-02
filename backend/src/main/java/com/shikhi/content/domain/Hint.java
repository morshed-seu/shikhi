package com.shikhi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Curated bilingual feedback for an exercise. {@code triggerKey} qualifies the trigger: for
 * {@code WRONG_ANSWER} it is a normalized mistaken answer; for {@code PATTERN} it is an
 * L1-pattern code (also reported as the verdict's {@code matchedPatternCode}).
 */
@Entity
@Table(name = "hints")
public class Hint {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "exercise_id", nullable = false)
	private UUID exerciseId;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger", nullable = false)
	private HintTrigger trigger;

	@Column(name = "trigger_key")
	private String triggerKey;

	@Column(name = "text_en", nullable = false)
	private String textEn;

	@Column(name = "text_bn", nullable = false)
	private String textBn;

	protected Hint() {
		// for JPA
	}

	public UUID getExerciseId() {
		return exerciseId;
	}

	public HintTrigger getTrigger() {
		return trigger;
	}

	public String getTriggerKey() {
		return triggerKey;
	}

	public String getTextEn() {
		return textEn;
	}

	public String getTextBn() {
		return textBn;
	}
}
