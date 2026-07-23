package com.shikhi.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Local practice/mastery state (OF4, docs/93-offline-learning-design.md §4.2) — an input to the
 * sync stream, not a 1:1 mirror of the server's `practice_word_progress` table. `masteryScore`
 * defaults to [com.shikhi.app.data.practice.UNSEEN_MASTERY] (2) for a brand-new row, matching the
 * server's `PracticeWordProgress.UNSEEN_MASTERY`/the picker's `COALESCE(mastery_score, 2)`.
 * Timestamps are epoch millis (`Instant.toEpochMilli()`), matching this file's existing
 * `Long`-timestamp convention ([OutboxEventEntity.createdAt], [CachedPayload.updatedAt]).
 */
@Entity(tableName = "local_word_progress", primaryKeys = ["userId", "vocabularyId"])
data class LocalWordProgress(
	val userId: String,
	val vocabularyId: String,
	val timesSeen: Int = 0,
	val timesCorrect: Int = 0,
	val timesWrong: Int = 0,
	val masteryScore: Int = 2,
	val lastWrongAt: Long? = null,
	val lastSeenAt: Long = 0L,
)

/**
 * A word's position on the local review ladder — only exists once a word has graduated out of
 * plain mastery tracking (mirrors the server's `review_progress`, which only has a row per
 * graduated word). See [com.shikhi.app.data.practice.WordProgressEngine].
 */
@Entity(tableName = "local_review_progress", primaryKeys = ["userId", "vocabularyId"])
data class LocalReviewProgress(
	val userId: String,
	val vocabularyId: String,
	val reviewStage: Int,
	val dueAt: Long,
	val lastReviewedAt: Long? = null,
	val reviewCount: Int = 0,
	val successfulReviews: Int = 0,
	val failedReviews: Int = 0,
	val failureStreak: Int = 0,
	val lastFailureAt: Long? = null,
)

@Dao
interface WordProgressDao {

	@Query("SELECT * FROM local_word_progress WHERE userId = :userId AND vocabularyId = :vocabularyId")
	suspend fun getWordProgress(userId: String, vocabularyId: String): LocalWordProgress?

	@Query("SELECT * FROM local_word_progress WHERE userId = :userId AND vocabularyId IN (:vocabularyIds)")
	suspend fun getWordProgressFor(userId: String, vocabularyIds: List<String>): List<LocalWordProgress>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(progress: LocalWordProgress)

	@Query("SELECT * FROM local_review_progress WHERE userId = :userId AND vocabularyId = :vocabularyId")
	suspend fun getReviewProgress(userId: String, vocabularyId: String): LocalReviewProgress?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertReview(progress: LocalReviewProgress)

	// OG2 (docs/94-offline-guest-bootstrap-design.md §3.3): re-key a local guest's rows onto the
	// server-issued userId once GuestRegistrationWorker registers it. Both queries run inside the
	// same db.withTransaction as LocalPracticeSessionDao.rekey — see ADR-0014 (partial re-key
	// would split one guest's history across two ids).
	@Query("UPDATE local_word_progress SET userId = :newUserId WHERE userId = :oldUserId")
	suspend fun rekey(oldUserId: String, newUserId: String)

	@Query("UPDATE local_review_progress SET userId = :newUserId WHERE userId = :oldUserId")
	suspend fun rekeyReview(oldUserId: String, newUserId: String)

	// UO6 (docs/95 §3.2): the pull-rebuild overwrite — delete-then-insert-all inside
	// ProgressPullRepository's single db.withTransaction, mirroring the flush-time
	// delete-and-collapse pattern OutboxRepository.flush() already uses.
	@Query("DELETE FROM local_word_progress WHERE userId = :userId")
	suspend fun deleteAllForUser(userId: String)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(rows: List<LocalWordProgress>)

	@Query("DELETE FROM local_review_progress WHERE userId = :userId")
	suspend fun deleteAllReviewForUser(userId: String)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAllReview(rows: List<LocalReviewProgress>)
}

/** One continuous local practice run (OF4 §4.2) — the offline mirror of `practice_sessions`. */
@Entity(tableName = "local_practice_sessions")
data class LocalPracticeSession(
	@PrimaryKey val id: String,
	val userId: String,
	val cefrLevel: String,
	val status: String,
	val roundsPlayed: Int = 0,
	val correctCount: Int = 0,
	val totalCount: Int = 0,
	val startedAt: Long,
	val completedAt: Long? = null,
)

/**
 * One generated-and-persisted local exercise (OF4 §4.2). Ephemeral by design (safe to prune once
 * its answer syncs) — kept only so [com.shikhi.app.data.practice.LocalPracticeSource.grade] can
 * look the answer key back up by exercise id without threading it through the UI layer.
 */
@Entity(tableName = "local_practice_exercises")
data class LocalPracticeExercise(
	@PrimaryKey val id: String,
	val sessionId: String,
	val round: Int,
	val ordinal: Int,
	val vocabularyId: String,
	val type: String,
	val promptEn: String,
	val promptBn: String,
	val payloadJson: String,
	val answerKeyJson: String,
	val answeredCorrect: Boolean? = null,
)

@Dao
interface LocalPracticeSessionDao {

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertSession(session: LocalPracticeSession)

	@Query("SELECT * FROM local_practice_sessions WHERE id = :id")
	suspend fun getSession(id: String): LocalPracticeSession?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertExercises(exercises: List<LocalPracticeExercise>)

	@Query("SELECT * FROM local_practice_exercises WHERE id = :id")
	suspend fun getExercise(id: String): LocalPracticeExercise?

	@Query("UPDATE local_practice_exercises SET answeredCorrect = :correct WHERE id = :id")
	suspend fun markAnswered(id: String, correct: Boolean)

	@Query("SELECT vocabularyId FROM local_practice_exercises WHERE sessionId = :sessionId")
	suspend fun usedVocabularyIds(sessionId: String): List<String>

	// OG2: see WordProgressDao.rekey — local_practice_exercises has no userId column (keyed by
	// sessionId), so it needs no equivalent query.
	@Query("UPDATE local_practice_sessions SET userId = :newUserId WHERE userId = :oldUserId")
	suspend fun rekey(oldUserId: String, newUserId: String)
}
