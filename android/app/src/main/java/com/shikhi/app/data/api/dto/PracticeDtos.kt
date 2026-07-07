package com.shikhi.app.data.api.dto

import kotlinx.serialization.Serializable

// Wire shapes mirror frontend/src/api/{practice,review,vocabulary,auth}.ts and docs/43.

// ---- Adaptive practice (E12) ----

/** CEFR bands a learner can practice at; C1 vocabulary exists but E12 stops at B2. */
val CEFR_LEVELS = listOf("A1", "A2", "B1", "B2")

/** The band after [level], or null at the top (web `nextLevel`). */
fun nextLevel(level: String): String? {
	val i = CEFR_LEVELS.indexOf(level)
	return if (i >= 0 && i + 1 < CEFR_LEVELS.size) CEFR_LEVELS[i + 1] else null
}

@Serializable
data class PracticeExerciseConfig(
	val options: List<ChoiceOption>? = null,
	val tokens: List<ChoiceOption>? = null,
	/** SENTENCE_GAP: Bengali rendering of the sentence, shown as a hint. */
	val contextBn: String? = null,
	/** SENTENCE_BUILD: the Bengali sentence to build in English. */
	val targetBn: String? = null,
	/** TYPE_WORD: part-of-speech hint. */
	val partOfSpeech: String? = null,
)

@Serializable
data class PracticeExercise(
	val id: String,
	val type: String,
	val ordinal: Int = 0,
	val prompt: Bilingual,
	val config: PracticeExerciseConfig = PracticeExerciseConfig(),
)

@Serializable
data class PracticeRound(
	val sessionId: String,
	val round: Int = 1,
	val cefrLevel: String = "A1",
	val levelUpEligible: Boolean = false,
	val exercises: List<PracticeExercise> = emptyList(),
)

@Serializable
data class PracticeResult(
	val correctCount: Int = 0,
	val totalCount: Int = 0,
	val roundsPlayed: Int = 0,
	val xpEarned: Int = 0,
	val levelUpEligible: Boolean = false,
	val stats: Stats = Stats(),
)

@Serializable
data class SetLevelRequest(val cefrLevel: String)

// ---- Spaced-repetition review ----

@Serializable
data class ReviewItem(
	val exerciseId: String,
	val prompt: Bilingual,
	val boxLevel: Int = 0,
	val dueAt: String = "",
)

@Serializable
data class ReviewResult(val exerciseId: String, val correct: Boolean)

@Serializable
data class ReviewResultsRequest(val results: List<ReviewResult>)

// ---- Vocabulary browser ----

@Serializable
data class VocabularyEntry(
	val id: String,
	val headword: String,
	val senseLabel: String? = null,
	val partOfSpeech: String = "",
	val cefrLevel: String = "A1",
	val bnGloss: String = "",
	val exampleEn: String? = null,
	val exampleBn: String? = null,
)

// ---- Accounts ----

@Serializable
data class RegisterRequest(
	val email: String,
	val password: String,
	val displayName: String? = null,
	val uiLocale: String? = null,
)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class ClaimRequest(
	val email: String,
	val password: String,
	val displayName: String? = null,
)
