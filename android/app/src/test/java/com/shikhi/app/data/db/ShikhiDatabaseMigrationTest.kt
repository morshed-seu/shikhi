package com.shikhi.app.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OF4 (docs/93-offline-learning-design.md §9 risk 2): [ShikhiDatabase] now holds durable per-user
 * mastery/review state, so the v2->v3 schema bump (the four new local practice/mastery tables)
 * must be a real [androidx.room.migration.Migration], not a `fallbackToDestructiveMigration` that
 * would silently wipe an existing learner's local outbox/cache on the next app update.
 *
 * This project has no Room schema-export configured (`exportSchema = false`), so there's no
 * generated schema JSON to drive `androidx.room:room-testing`'s `MigrationTestHelper` off of.
 * Instead, this test hand-builds a real v2 SQLite file (the exact DDL Room itself generates for
 * [OutboxEventEntity]/[CachedPayload] — the two entities that existed at v2), stamps it to
 * `PRAGMA user_version = 2`, then opens it through Room with [MIGRATION_2_3] registered. Room's
 * own post-migration schema validation (comparing the actual on-disk table structure against what
 * it expects from the current `@Entity` declarations) is what actually proves the DDL in
 * [MIGRATION_2_3] is correct — if it were wrong, this would fail with `IllegalStateException:
 * Migration didn't properly handle ...`, not silently pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShikhiDatabaseMigrationTest {

	private val dbName = "migration-test.db"

	@Test
	fun `migration 2 to 3 adds the four new tables and preserves existing v2 rows`() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		val dbFile = context.getDatabasePath(dbName)
		dbFile.parentFile?.mkdirs()
		dbFile.delete()

		// Hand-built v2 schema, matching what Room itself generates for OutboxEventEntity/CachedPayload.
		val v2 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
		v2.execSQL(
			"CREATE TABLE IF NOT EXISTS `outbox_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
				"`idempotencyKey` TEXT NOT NULL, `type` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
		)
		v2.execSQL(
			"CREATE TABLE IF NOT EXISTS `content_cache` (`key` TEXT NOT NULL, `json` TEXT NOT NULL, " +
				"`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`key`))",
		)
		v2.execSQL(
			"INSERT INTO outbox_events (idempotencyKey, type, payloadJson, createdAt) VALUES " +
				"('idem-1', 'COMPLETE_LESSON', '{}', 100)",
		)
		v2.version = 2
		v2.close()

		val migrated = Room.databaseBuilder(context, ShikhiDatabase::class.java, dbName)
			.addMigrations(MIGRATION_2_3)
			.build()
		try {
			// Pre-existing data survived the migration untouched.
			val outboxRows = runBlocking { migrated.outboxDao().all() }
			assertEquals(1, outboxRows.size)
			assertEquals("idem-1", outboxRows.single().idempotencyKey)

			// The four new tables are real, usable Room tables (this also exercises Room's
			// post-migration schema validation — a DDL mismatch here throws before this point).
			runBlocking {
				migrated.wordProgressDao().upsert(
					LocalWordProgress(userId = "u1", vocabularyId = "v1", masteryScore = 4, lastSeenAt = 0L),
				)
				migrated.localPracticeSessionDao().upsertSession(
					LocalPracticeSession(id = "s1", userId = "u1", cefrLevel = "A1", status = "IN_PROGRESS", startedAt = 0L),
				)
				migrated.localPracticeSessionDao().insertExercises(
					listOf(
						LocalPracticeExercise(
							id = "e1", sessionId = "s1", round = 1, ordinal = 1, vocabularyId = "v1",
							type = "WORD_MEANING", promptEn = "p", promptBn = "p", payloadJson = "{}", answerKeyJson = "{}",
						),
					),
				)
			}

			val progress = runBlocking { migrated.wordProgressDao().getWordProgress("u1", "v1") }
			assertEquals(4, progress?.masteryScore)
			val session = runBlocking { migrated.localPracticeSessionDao().getSession("s1") }
			assertEquals("A1", session?.cefrLevel)
			val exercise = runBlocking { migrated.localPracticeSessionDao().getExercise("e1") }
			assertEquals("v1", exercise?.vocabularyId)
		} finally {
			migrated.close()
			dbFile.delete()
		}
	}

	@Test
	fun `a fresh install creates all tables directly at version 3 with no migration involved`() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		val db = Room.inMemoryDatabaseBuilder(context, ShikhiDatabase::class.java).build()
		try {
			runBlocking {
				db.wordProgressDao().upsert(LocalWordProgress(userId = "u1", vocabularyId = "v1", lastSeenAt = 0L))
			}
			val rows = runBlocking { db.wordProgressDao().getWordProgressFor("u1", listOf("v1")) }
			assertEquals(1, rows.size)
		} finally {
			db.close()
		}
	}
}
