package com.shikhi.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 -> v3 (OF4, docs/93-offline-learning-design.md §3.2/§9 risk 2): adds the four local
 * practice/mastery tables ([LocalWordProgress], [LocalReviewProgress], [LocalPracticeSession],
 * [LocalPracticeExercise]). This is the first `Migration` in [ShikhiDatabase]'s history — before
 * OF4 the database held only a disposable content cache + outbox, so
 * `fallbackToDestructiveMigration` was an acceptable stand-in; from this schema bump on, this
 * database holds durable per-user mastery/review state that must survive an app update, so a real
 * migration replaces it (a plain `CREATE TABLE` per new table — no data transformation needed,
 * since none of these tables existed before).
 *
 * Column types/nullability here are hand-derived from each entity's Kotlin declaration (`String`
 * -> `TEXT NOT NULL`, nullable -> no `NOT NULL`, `Int`/`Long`/`Boolean` -> `INTEGER NOT NULL`) —
 * this project has no Room schema-export configured (`exportSchema = false`, matching
 * [com.shikhi.app.data.content.db.ContentDatabase]'s existing choice), so there is no generated
 * schema JSON to diff against; [ShikhiDatabaseMigrationTest] is the executable check that this
 * DDL actually matches what Room expects from the entities below.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `local_word_progress` (
				`userId` TEXT NOT NULL,
				`vocabularyId` TEXT NOT NULL,
				`timesSeen` INTEGER NOT NULL,
				`timesCorrect` INTEGER NOT NULL,
				`timesWrong` INTEGER NOT NULL,
				`masteryScore` INTEGER NOT NULL,
				`lastWrongAt` INTEGER,
				`lastSeenAt` INTEGER NOT NULL,
				PRIMARY KEY(`userId`, `vocabularyId`)
			)
			""".trimIndent(),
		)
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `local_review_progress` (
				`userId` TEXT NOT NULL,
				`vocabularyId` TEXT NOT NULL,
				`reviewStage` INTEGER NOT NULL,
				`dueAt` INTEGER NOT NULL,
				`lastReviewedAt` INTEGER,
				`reviewCount` INTEGER NOT NULL,
				`successfulReviews` INTEGER NOT NULL,
				`failedReviews` INTEGER NOT NULL,
				`failureStreak` INTEGER NOT NULL,
				`lastFailureAt` INTEGER,
				PRIMARY KEY(`userId`, `vocabularyId`)
			)
			""".trimIndent(),
		)
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `local_practice_sessions` (
				`id` TEXT NOT NULL,
				`userId` TEXT NOT NULL,
				`cefrLevel` TEXT NOT NULL,
				`status` TEXT NOT NULL,
				`roundsPlayed` INTEGER NOT NULL,
				`correctCount` INTEGER NOT NULL,
				`totalCount` INTEGER NOT NULL,
				`startedAt` INTEGER NOT NULL,
				`completedAt` INTEGER,
				PRIMARY KEY(`id`)
			)
			""".trimIndent(),
		)
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `local_practice_exercises` (
				`id` TEXT NOT NULL,
				`sessionId` TEXT NOT NULL,
				`round` INTEGER NOT NULL,
				`ordinal` INTEGER NOT NULL,
				`vocabularyId` TEXT NOT NULL,
				`type` TEXT NOT NULL,
				`promptEn` TEXT NOT NULL,
				`promptBn` TEXT NOT NULL,
				`payloadJson` TEXT NOT NULL,
				`answerKeyJson` TEXT NOT NULL,
				`answeredCorrect` INTEGER,
				PRIMARY KEY(`id`)
			)
			""".trimIndent(),
		)
	}
}

/**
 * v3 -> v4 (UO2, docs/95-unified-offline-online-design.md §4/§8 risk 1): adds
 * [LocalStatsProjection], the durable on-device stats table that replaces the ad-hoc `"stats"`
 * `content_cache` blob as offline display's source of truth (nothing reads it yet in this gate --
 * see the doc's §7 gate sequence: `UO4`/`UO6` are the readers). Same reasoning as
 * [MIGRATION_2_3]'s doc comment applies here: this table holds durable per-user state (a
 * reconciled XP baseline + non-additive hearts/streak fields), so a real `Migration` is required
 * rather than falling back to a destructive one that would silently reset a learner's projection
 * on the next app update.
 *
 * Column types/nullability hand-derived from [LocalStatsProjection]'s Kotlin declaration, same
 * convention as [MIGRATION_2_3]: `String` -> `TEXT NOT NULL`, nullable -> no `NOT NULL`,
 * `Int`/`Long` -> `INTEGER NOT NULL`. [ShikhiDatabaseMigrationTest] is the executable proof this
 * DDL matches what Room expects from the entity below (`exportSchema = false`, so there is no
 * generated schema JSON to diff against instead).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `local_stats_projection` (
				`userId` TEXT NOT NULL,
				`baselineXp` INTEGER NOT NULL,
				`hearts` INTEGER NOT NULL,
				`currentStreak` INTEGER NOT NULL,
				`longestStreak` INTEGER NOT NULL,
				`cefrLevel` TEXT NOT NULL,
				`lastActiveDate` TEXT,
				`rank` INTEGER NOT NULL,
				`dailyGoal` INTEGER NOT NULL,
				`updatedAt` INTEGER NOT NULL,
				PRIMARY KEY(`userId`)
			)
			""".trimIndent(),
		)
	}
}

/**
 * v4 -> v5 (UO4, `~/.claude/plans/unified-offline-online/UO4.md`): two schema changes landing
 * together, same "bundle related changes into one Migration" precedent as [MIGRATION_2_3]'s four
 * `CREATE TABLE`s in one bump. First, [LocalLessonCompletion] — the local mirror of the server's
 * `UserProgress` first-completion gate, letting offline lesson completion award real XP exactly
 * once per lesson instead of the flat 0 UO4 replaces. Second, a nullable `reconciledAt` column on
 * `local_stats_projection` — the durable "has this row ever actually synced" marker
 * [com.shikhi.app.data.progress.StatsProjectionRepository.hasReconciled] reads, which
 * `OfflineCopyBanner` now keys off instead of the current fetch's `fromCache` flag. Both are
 * durable per-user state (the completion ledger drives XP correctness; `reconciledAt` drives a
 * user-visible banner), so — same reasoning as [MIGRATION_2_3]/[MIGRATION_3_4]'s doc comments —
 * a real `Migration` is required, not a destructive fallback that would silently reset either on
 * the next app update.
 *
 * Column types/nullability hand-derived from [LocalLessonCompletion]'s Kotlin declaration and
 * [LocalStatsProjection]'s new field, same convention as the migrations above.
 * [ShikhiDatabaseMigrationTest] is the executable proof this DDL matches what Room expects
 * (`exportSchema = false`, so there is no generated schema JSON to diff against instead).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
	override fun migrate(db: SupportSQLiteDatabase) {
		db.execSQL(
			"""
			CREATE TABLE IF NOT EXISTS `local_lesson_completion` (
				`userId` TEXT NOT NULL,
				`lessonId` TEXT NOT NULL,
				`contentVersionId` TEXT NOT NULL,
				`firstCompletionEventId` INTEGER NOT NULL,
				`completedAt` INTEGER NOT NULL,
				PRIMARY KEY(`userId`, `lessonId`, `contentVersionId`)
			)
			""".trimIndent(),
		)
		db.execSQL("ALTER TABLE `local_stats_projection` ADD COLUMN `reconciledAt` INTEGER")
	}
}
