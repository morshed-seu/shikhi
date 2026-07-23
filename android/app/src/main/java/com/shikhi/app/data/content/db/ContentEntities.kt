package com.shikhi.app.data.content.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Bundled, read-only content tables (OF1, docs/93-offline-learning-design.md §4.1). Field-for-
 * field mirror of the backend export shape produced by `ContentExportTool`
 * (`backend/src/main/java/com/shikhi/content/tool/ContentExportTool.java`) and the JSON assets
 * at `assets/content-seed/{vocabulary,curriculum}.json`. Reseeded wholesale from a new APK
 * build — never migrated row-by-row.
 */

@Entity(tableName = "local_vocabulary")
data class LocalVocabulary(
	@PrimaryKey val id: String,
	val headword: String,
	val senseLabel: String?,
	val partOfSpeech: String,
	val cefrLevel: String,
	val bnGloss: String,
	val exampleEn: String?,
	val exampleBn: String?,
	val ordinal: Int,
)

@Entity(tableName = "local_levels")
data class LocalLevel(
	@PrimaryKey val id: String,
	val code: String,
	val titleEn: String,
	val titleBn: String,
	val ordinal: Int,
)

@Entity(tableName = "local_units")
data class LocalUnit(
	@PrimaryKey val id: String,
	val levelId: String,
	val code: String,
	val titleEn: String,
	val titleBn: String,
	val ordinal: Int,
)

@Entity(tableName = "local_lessons")
data class LocalLesson(
	@PrimaryKey val id: String,
	val unitId: String,
	val code: String,
	val titleEn: String,
	val titleBn: String,
	val ordinal: Int,
)

// NOTE: the design doc's §4.1 sketch lists `configJson`/`patternTags` on LocalExercise, but the
// real backend `Exercise` entity (backend/src/main/java/com/shikhi/content/domain/Exercise.java)
// has no such columns — those two fields don't exist in the schema today, so they're omitted
// here to stay a true field-for-field mirror of what `ContentExportTool` actually exports.
@Entity(tableName = "local_exercises")
data class LocalExercise(
	@PrimaryKey val id: String,
	val lessonId: String,
	val type: String,
	val ordinal: Int,
	val promptEn: String,
	val promptBn: String,
	val mediaRef: String?,
)

/** Answer key. Never queried from [ContentReadDao] — see [ContentAnswerKeyDao]. */
@Entity(tableName = "local_exercise_options")
data class LocalExerciseOption(
	@PrimaryKey val id: String,
	val exerciseId: String,
	val textEn: String,
	val textBn: String,
	val isCorrect: Boolean,
	val ordinal: Int,
)

/** Answer key. Never queried from [ContentReadDao] — see [ContentAnswerKeyDao]. */
@Entity(tableName = "local_exercise_answers")
data class LocalExerciseAnswer(
	@PrimaryKey val id: String,
	val exerciseId: String,
	val acceptedAnswer: String,
	val isPrimary: Boolean,
)

@Entity(tableName = "local_hints")
data class LocalHint(
	@PrimaryKey val id: String,
	val exerciseId: String,
	val trigger: String,
	val triggerKey: String?,
	val textEn: String,
	val textBn: String,
)
