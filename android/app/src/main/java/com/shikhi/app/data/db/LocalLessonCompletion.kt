package com.shikhi.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * UO4: the local mirror of the server's `UserProgress` first-completion gate
 * (`ProgressService.completeLesson`'s firstCompletion check) — bundled content has no live
 * `contentVersion` (OF2: `LocalLessonSource`'s `LessonView.contentVersion` is always blank), so
 * [contentVersionId] is always the empty string here, kept only so the shape mirrors the
 * server's (user, lesson, version) key exactly. [firstCompletionEventId] pins this row to the
 * exact outbox event id whose COMPLETE_LESSON payload is allowed to still contribute XP in
 * [com.shikhi.app.data.progress.StatsProjectionRepository]'s pending-XP scan — matching purely
 * on "does a row exist for this lesson" would incorrectly zero out the very completion that
 * first created the row (it's still pending, not yet synced, at the moment the row is written);
 * pinning the causing event's id instead makes the check exact regardless of ordering.
 */
@Entity(tableName = "local_lesson_completion", primaryKeys = ["userId", "lessonId", "contentVersionId"])
data class LocalLessonCompletion(
	val userId: String,
	val lessonId: String,
	val contentVersionId: String,
	val firstCompletionEventId: Long,
	val completedAt: Long,
)

@Dao
interface LocalLessonCompletionDao {

	@Query("SELECT * FROM local_lesson_completion WHERE userId = :userId AND lessonId = :lessonId AND contentVersionId = :contentVersionId")
	suspend fun get(userId: String, lessonId: String, contentVersionId: String): LocalLessonCompletion?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(row: LocalLessonCompletion)

	// UO4: same re-key pattern as LocalStatsProjectionDao.rekey — runs inside
	// GuestRegistrationWorker's existing db.withTransaction so a guest's lesson-completion ledger
	// survives LocalGuest -> Active alongside its stats projection/mastery/review/session rows.
	@Query("UPDATE local_lesson_completion SET userId = :newUserId WHERE userId = :oldUserId")
	suspend fun rekey(oldUserId: String, newUserId: String)

	// UO6 (docs/95 §3.2): the pull-rebuild overwrite, same delete-then-insert-all pattern as
	// WordProgressDao's new methods.
	@Query("DELETE FROM local_lesson_completion WHERE userId = :userId")
	suspend fun deleteAllForUser(userId: String)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertAll(rows: List<LocalLessonCompletion>)
}
