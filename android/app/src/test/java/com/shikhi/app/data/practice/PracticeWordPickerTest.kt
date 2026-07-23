package com.shikhi.app.data.practice

import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.content.db.LocalExercise
import com.shikhi.app.data.content.db.LocalLesson
import com.shikhi.app.data.content.db.LocalLevel
import com.shikhi.app.data.content.db.LocalUnit
import com.shikhi.app.data.content.db.LocalVocabulary
import com.shikhi.app.data.db.LocalWordProgress
import com.shikhi.app.data.db.WordProgressDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * [PracticeWordPicker] reproduces the backend's `ORDER BY COALESCE(mastery_score, 2), RANDOM()`
 * weakest-first ordering in Kotlin (two separate Room databases, no cross-DB join — see the
 * class doc). These tests pin that ordering and the exclusion/limit behavior.
 */
class PracticeWordPickerTest {

	private class FakeContentReadDao(private val byLevel: Map<String, List<LocalVocabulary>>) : ContentReadDao {
		override suspend fun getVocabularyByLevel(level: String): List<LocalVocabulary> = byLevel[level].orEmpty()
		override suspend fun getLevels(): List<LocalLevel> = emptyList()
		override suspend fun getUnitsForLevel(levelId: String): List<LocalUnit> = emptyList()
		override suspend fun getLessonsForUnit(unitId: String): List<LocalLesson> = emptyList()
		override suspend fun getLesson(lessonId: String): LocalLesson? = null
		override suspend fun getExercisesForLesson(lessonId: String): List<LocalExercise> = emptyList()
		override suspend fun insertVocabulary(rows: List<LocalVocabulary>) = Unit
		override suspend fun insertLevels(rows: List<LocalLevel>) = Unit
		override suspend fun insertUnits(rows: List<LocalUnit>) = Unit
		override suspend fun insertLessons(rows: List<LocalLesson>) = Unit
		override suspend fun insertExercises(rows: List<LocalExercise>) = Unit
		override suspend fun vocabularyCount(): Int = byLevel.values.sumOf { it.size }
		override suspend fun lessonCount(): Int = 0
	}

	private class FakeWordProgressDao : WordProgressDao {
		val rows = mutableListOf<LocalWordProgress>()
		override suspend fun getWordProgress(userId: String, vocabularyId: String): LocalWordProgress? =
			rows.find { it.userId == userId && it.vocabularyId == vocabularyId }
		override suspend fun getWordProgressFor(userId: String, vocabularyIds: List<String>): List<LocalWordProgress> =
			rows.filter { it.userId == userId && it.vocabularyId in vocabularyIds }
		override suspend fun upsert(progress: LocalWordProgress) { rows.add(progress) }
		override suspend fun getReviewProgress(userId: String, vocabularyId: String) = null
		override suspend fun upsertReview(progress: com.shikhi.app.data.db.LocalReviewProgress) = Unit
		override suspend fun rekey(oldUserId: String, newUserId: String) = Unit
		override suspend fun rekeyReview(oldUserId: String, newUserId: String) = Unit
		// UO6: pull-rebuild overwrite methods — not exercised by this file's tests.
		override suspend fun deleteAllForUser(userId: String) {
			rows.removeAll { it.userId == userId }
		}
		override suspend fun upsertAll(rows: List<LocalWordProgress>) {
			rows.forEach { upsert(it) }
		}
		override suspend fun deleteAllReviewForUser(userId: String) = Unit
		override suspend fun upsertAllReview(rows: List<com.shikhi.app.data.db.LocalReviewProgress>) = Unit
	}

	private fun vocab(id: String, level: String = "A1") = LocalVocabulary(
		id = id, headword = id, senseLabel = null, partOfSpeech = "noun",
		cefrLevel = level, bnGloss = "gloss-$id", exampleEn = null, exampleBn = null, ordinal = 1,
	)

	@Test
	fun `weakest words (lowest mastery) are picked before mastered words`() = runBlocking {
		val weak = vocab("weak")
		val mastered = vocab("mastered")
		val unseen = vocab("unseen")
		val contentDao = FakeContentReadDao(mapOf("A1" to listOf(weak, mastered, unseen)))
		val progressDao = FakeWordProgressDao().apply {
			rows += LocalWordProgress("user-1", "weak", masteryScore = 0)
			rows += LocalWordProgress("user-1", "mastered", masteryScore = 5)
			// "unseen" has no row -> defaults to UNSEEN_MASTERY (2), between weak and mastered.
		}
		val picker = PracticeWordPicker(contentDao, progressDao, Random(1))

		val picked = picker.pick("user-1", listOf("A1"), emptyList(), limit = 3)

		assertEquals(listOf("weak", "unseen", "mastered"), picked.map { it.id })
	}

	@Test
	fun `used ids are excluded from the pick`() = runBlocking {
		val a = vocab("a")
		val b = vocab("b")
		val contentDao = FakeContentReadDao(mapOf("A1" to listOf(a, b)))
		val picker = PracticeWordPicker(contentDao, FakeWordProgressDao(), Random(1))

		val picked = picker.pick("user-1", listOf("A1"), usedIds = listOf("a"), limit = 5)

		assertEquals(listOf("b"), picked.map { it.id })
	}

	@Test
	fun `pick never returns more than the requested limit`() = runBlocking {
		val words = (1..10).map { vocab("w$it") }
		val contentDao = FakeContentReadDao(mapOf("A1" to words))
		val picker = PracticeWordPicker(contentDao, FakeWordProgressDao(), Random(1))

		val picked = picker.pick("user-1", listOf("A1"), emptyList(), limit = 4)

		assertEquals(4, picked.size)
	}

	@Test
	fun `distractorPool never returns more than the requested limit and stays within the band`() = runBlocking {
		val a1 = (1..5).map { vocab("a1-$it", "A1") }
		val a2 = (1..5).map { vocab("a2-$it", "A2") }
		val contentDao = FakeContentReadDao(mapOf("A1" to a1, "A2" to a2))
		val picker = PracticeWordPicker(contentDao, FakeWordProgressDao(), Random(1))

		val pool = picker.distractorPool("A1", limit = 3)

		assertEquals(3, pool.size)
		assertTrue(pool.all { it.cefrLevel == "A1" })
	}
}
