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
