package com.shikhi.content.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A playable lesson (contract {@code Lesson}). Exercises carry only render data in
 * {@code config}; correct answers/option flags are never included (grading is server-side).
 */
public record LessonView(UUID id, String contentVersion, Bilingual title,
		List<ExerciseView> exercises) {

	public record ExerciseView(UUID id, String type, int ordinal, Bilingual prompt,
			String mediaRef, List<String> patternTags, Map<String, Object> config) {
	}
}
