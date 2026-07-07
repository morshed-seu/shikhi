package com.shikhi.app.data.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Wire shapes mirror docs/43-api-contract.openapi.yaml and the web client modules
// (frontend/src/api/{curriculum,sessions,outbox}.ts). Schemaless payloads (exercise
// answers, sync-event payloads) are JsonObject — see ADR-0012 on why not Map<String, Any>.

@Serializable
data class Bilingual(val en: String = "", val bn: String = "")

// ---- Curriculum tree ----

@Serializable
data class LessonNode(
	val id: String,
	val title: Bilingual,
	val ordinal: Int = 0,
	val status: String = "NOT_STARTED",
	val locked: Boolean = false,
)

@Serializable
data class UnitNode(
	val id: String,
	val code: String = "",
	val title: Bilingual,
	val ordinal: Int = 0,
	val locked: Boolean = false,
	val lessons: List<LessonNode> = emptyList(),
)

@Serializable
data class LevelNode(
	val id: String,
	val code: String = "",
	val title: Bilingual,
	val ordinal: Int = 0,
	val units: List<UnitNode> = emptyList(),
)

@Serializable
data class CurriculumTree(
	val contentVersion: String? = null,
	val levels: List<LevelNode> = emptyList(),
)

// ---- Playable lesson ----

/** An MCQ option or word-bank token: id + bilingual text (web `McqOption`/`WordToken`). */
@Serializable
data class ChoiceOption(val id: String, val text: Bilingual)

/** Render-only exercise config — never carries correctness (grading is server-side). */
@Serializable
data class ExerciseConfig(
	val options: List<ChoiceOption>? = null,
	val tokens: List<ChoiceOption>? = null,
)

@Serializable
data class Exercise(
	val id: String,
	val type: String,
	val ordinal: Int = 0,
	val prompt: Bilingual,
	val mediaRef: String? = null,
	val patternTags: List<String> = emptyList(),
	val config: ExerciseConfig = ExerciseConfig(),
)

@Serializable
data class LessonView(
	val id: String,
	val contentVersion: String = "",
	val title: Bilingual,
	val exercises: List<Exercise> = emptyList(),
)

// ---- Lesson session ----

@Serializable
data class StartSessionRequest(val lessonId: String)

@Serializable
data class LessonSession(
	val id: String,
	val lessonId: String,
	val contentVersion: String = "",
	val heartsRemaining: Int = 0,
	val status: String = "IN_PROGRESS",
)

@Serializable
data class SubmitAnswerRequest(
	val idempotencyKey: String,
	val exerciseId: String,
	/** Type-specific: {selectedOptionId} (MCQ) or {tokenOrder} (WORD_BANK). */
	val answer: JsonObject,
)

@Serializable
data class Verdict(
	val correct: Boolean,
	val feedback: Bilingual? = null,
	val matchedPatternCode: String? = null,
	val source: String = "RULE",
)

@Serializable
data class Stats(
	val hearts: Int = 0,
	val xp: Int = 0,
	val currentStreak: Int = 0,
	val longestStreak: Int = 0,
	val rank: Int = 0,
	val dailyGoal: Int = 0,
	val cefrLevel: String = "A1",
	val accuracyByPattern: Map<String, Double> = emptyMap(),
)

@Serializable
data class AnswerResult(val verdict: Verdict, val stats: Stats)

@Serializable
data class IdempotentRequest(val idempotencyKey: String)

@Serializable
data class LessonResult(
	val score: Int = 0,
	val xpEarned: Int = 0,
	val newlyUnlocked: List<String> = emptyList(),
	val reviewItemsAdded: Int = 0,
	val stats: Stats = Stats(),
)

// ---- Progress sync (offline outbox) ----

@Serializable
data class SyncEvent(
	val idempotencyKey: String,
	val type: String,
	val payload: JsonObject,
)

@Serializable
data class SyncBatchRequest(val events: List<SyncEvent>)
