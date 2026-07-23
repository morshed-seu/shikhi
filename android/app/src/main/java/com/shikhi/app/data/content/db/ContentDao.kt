package com.shikhi.app.data.content.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * UI-shaped reads over the bundled content (OF1, docs/93-offline-learning-design.md §4.1).
 * Deliberately does not touch [LocalExerciseOption]/[LocalExerciseAnswer] — the answer-key
 * tables — so nothing consumed by `CachedContentRepository` or any lesson/practice UI can leak
 * a correct answer. Grading-only access to those tables goes through [ContentAnswerKeyDao].
 */
@Dao
interface ContentReadDao {

	@Query("SELECT * FROM local_vocabulary WHERE cefrLevel = :level ORDER BY ordinal")
	suspend fun getVocabularyByLevel(level: String): List<LocalVocabulary>

	@Query("SELECT * FROM local_levels ORDER BY ordinal")
	suspend fun getLevels(): List<LocalLevel>

	@Query("SELECT * FROM local_units WHERE levelId = :levelId ORDER BY ordinal")
	suspend fun getUnitsForLevel(levelId: String): List<LocalUnit>

	@Query("SELECT * FROM local_lessons WHERE unitId = :unitId ORDER BY ordinal")
	suspend fun getLessonsForUnit(unitId: String): List<LocalLesson>

	/** Single-lesson lookup for offline lesson play (OF3) — same table, no answer-key columns. */
	@Query("SELECT * FROM local_lessons WHERE id = :lessonId")
	suspend fun getLesson(lessonId: String): LocalLesson?

	@Query(
		"SELECT id, lessonId, type, ordinal, promptEn, promptBn, mediaRef " +
			"FROM local_exercises WHERE lessonId = :lessonId ORDER BY ordinal",
	)
	suspend fun getExercisesForLesson(lessonId: String): List<LocalExercise>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertVocabulary(rows: List<LocalVocabulary>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertLevels(rows: List<LocalLevel>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertUnits(rows: List<LocalUnit>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertLessons(rows: List<LocalLesson>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertExercises(rows: List<LocalExercise>)

	@Query("SELECT COUNT(*) FROM local_vocabulary")
	suspend fun vocabularyCount(): Int

	@Query("SELECT COUNT(*) FROM local_lessons")
	suspend fun lessonCount(): Int
}

/**
 * Answer-key-only DAO: the sole read/write surface for [LocalExerciseOption],
 * [LocalExerciseAnswer], and [LocalHint]. Consumed by the local grading engine (OF3/OF4) —
 * must never be injected into UI-facing code (ViewModels, Composables, `CachedContentRepository`)
 * since that would let a correct answer reach the client's rendered UI state.
 */
@Dao
interface ContentAnswerKeyDao {

	@Query("SELECT * FROM local_exercise_options WHERE exerciseId = :exerciseId ORDER BY ordinal")
	suspend fun getOptions(exerciseId: String): List<LocalExerciseOption>

	@Query("SELECT * FROM local_exercise_answers WHERE exerciseId = :exerciseId")
	suspend fun getAnswers(exerciseId: String): List<LocalExerciseAnswer>

	@Query("SELECT * FROM local_hints WHERE exerciseId = :exerciseId")
	suspend fun getHints(exerciseId: String): List<LocalHint>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertOptions(rows: List<LocalExerciseOption>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertAnswers(rows: List<LocalExerciseAnswer>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertHints(rows: List<LocalHint>)
}
