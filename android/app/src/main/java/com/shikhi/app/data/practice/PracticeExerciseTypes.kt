package com.shikhi.app.data.practice

/**
 * Generated-exercise formats (E12), string-constant port of the backend's
 * `PracticeExerciseType` enum (`backend/src/main/java/com/shikhi/practice/domain/
 * PracticeExerciseType.java`) — kept as plain `String` constants rather than a Kotlin `enum class`
 * so [com.shikhi.app.data.practice.db.LocalPracticeExercise.type]/the wire
 * [com.shikhi.app.data.api.dto.PracticeExercise.type] can both use it directly, matching this
 * project's existing convention for exercise types (`com.shikhi.app.data.api.dto.Exercise.type`
 * is also a plain `String`, not an enum).
 */
object PracticeExerciseType {
	const val WORD_MEANING = "WORD_MEANING"
	const val MEANING_WORD = "MEANING_WORD"
	const val SENTENCE_GAP = "SENTENCE_GAP"
	const val SENTENCE_BUILD = "SENTENCE_BUILD"
	const val TYPE_WORD = "TYPE_WORD"
}
