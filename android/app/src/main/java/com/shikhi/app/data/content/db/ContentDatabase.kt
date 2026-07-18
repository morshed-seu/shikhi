package com.shikhi.app.data.content.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Bundled, read-only content database (OF1, docs/93-offline-learning-design.md §3.2, §4.1).
 * Separate from [com.shikhi.app.data.db.ShikhiDatabase] so the answer-key tables
 * ([LocalExerciseOption], [LocalExerciseAnswer]) sit behind [ContentAnswerKeyDao], a DAO surface
 * only the grading engine touches, never the UI-facing read paths.
 *
 * Reseeded wholesale from a new APK build (see `ContentSeedImporter`) — never migrated
 * row-by-row, so it's always safe to drop and reseed. `exportSchema = false` here matches
 * `ShikhiDatabase`'s existing choice; this project has no Room schema-export Gradle
 * configuration (no `room { schemaDirectory(...) }` block) for either database, so turning
 * export on for just this one database would be an inconsistent one-off rather than the "real
 * migrations from day one" tracking the design doc calls for — that needs the Gradle wiring
 * added for both databases together, which is out of scope for OF1.
 */
@Database(
	entities = [
		LocalVocabulary::class,
		LocalLevel::class,
		LocalUnit::class,
		LocalLesson::class,
		LocalExercise::class,
		LocalExerciseOption::class,
		LocalExerciseAnswer::class,
		LocalHint::class,
	],
	version = 1,
	exportSchema = false,
)
abstract class ContentDatabase : RoomDatabase() {
	abstract fun contentReadDao(): ContentReadDao

	abstract fun contentAnswerKeyDao(): ContentAnswerKeyDao
}
