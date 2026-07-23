package com.shikhi.app.data.content.seed

import kotlinx.serialization.Serializable

/**
 * JSON shapes of `assets/content-seed/{vocabulary,curriculum}.json`, produced by the backend's
 * `ContentExportTool` (`backend/src/main/java/com/shikhi/content/tool/ContentExportTool.java`,
 * OF1). Field names match the exported JSON keys exactly so no `@SerialName` mapping is needed.
 */

@Serializable
data class VocabularySeedDto(
	val id: String,
	val headword: String,
	val senseLabel: String? = null,
	val partOfSpeech: String,
	val cefrLevel: String,
	val bnGloss: String,
	val exampleEn: String? = null,
	val exampleBn: String? = null,
	val ordinal: Int,
)

@Serializable
data class LevelSeedDto(
	val id: String,
	val code: String,
	val titleEn: String,
	val titleBn: String,
	val ordinal: Int,
)

@Serializable
data class UnitSeedDto(
	val id: String,
	val levelId: String,
	val code: String,
	val titleEn: String,
	val titleBn: String,
	val ordinal: Int,
)

@Serializable
data class LessonSeedDto(
	val id: String,
	val unitId: String,
	val code: String,
	val titleEn: String,
	val titleBn: String,
	val ordinal: Int,
)

@Serializable
data class ExerciseSeedDto(
	val id: String,
	val lessonId: String,
	val type: String,
	val ordinal: Int,
	val promptEn: String,
	val promptBn: String,
	val mediaRef: String? = null,
)

@Serializable
data class ExerciseOptionSeedDto(
	val id: String,
	val exerciseId: String,
	val textEn: String,
	val textBn: String,
	val isCorrect: Boolean,
	val ordinal: Int,
)

@Serializable
data class ExerciseAnswerSeedDto(
	val id: String,
	val exerciseId: String,
	val acceptedAnswer: String,
	val isPrimary: Boolean,
)

@Serializable
data class HintSeedDto(
	val id: String,
	val exerciseId: String,
	val trigger: String,
	val triggerKey: String? = null,
	val textEn: String,
	val textBn: String,
)

@Serializable
data class CurriculumSeedDto(
	val levels: List<LevelSeedDto>,
	val units: List<UnitSeedDto>,
	val lessons: List<LessonSeedDto>,
	val exercises: List<ExerciseSeedDto>,
	val exerciseOptions: List<ExerciseOptionSeedDto>,
	val exerciseAnswers: List<ExerciseAnswerSeedDto>,
	val hints: List<HintSeedDto>,
)
