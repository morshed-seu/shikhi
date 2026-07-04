package com.shikhi.practice.web;

import com.shikhi.content.web.Bilingual;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.domain.PracticeExerciseType;
import java.util.Map;
import java.util.UUID;

/**
 * Contract {@code PracticeExercise} — what the learner sees. Built strictly from the
 * entity's payload; the answer key is never mapped here (grading is server-side).
 */
public record PracticeExerciseView(UUID id, PracticeExerciseType type, int ordinal,
		Bilingual prompt, Map<String, Object> config) {

	public static PracticeExerciseView from(PracticeExercise exercise) {
		return new PracticeExerciseView(exercise.getId(), exercise.getType(),
				exercise.getOrdinal(),
				new Bilingual(exercise.getPromptEn(), exercise.getPromptBn()),
				exercise.getPayload());
	}
}
