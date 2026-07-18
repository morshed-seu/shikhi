package com.shikhi.app.data.content.seed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shikhi.app.data.content.db.ContentDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gate OF1: verifies the real, bundled `assets/content-seed/{vocabulary,curriculum}.json`
 * import cleanly into a real (in-memory) [ContentDatabase] via [ContentSeedImporter], and that
 * the version gate skips a second import. Row counts pin the actual export snapshot taken
 * 2026-07-18 (backend `ContentExportTool` against a freshly migrated DB) — see
 * docs/93-offline-learning-design.md §6; the design doc's "~13 exercises" was an earlier
 * estimate, the real pilot content has 32.
 *
 * Runs under Robolectric (this project has no androidTest source set) so it gets a real
 * [Context]/`AssetManager` reading the actual packaged assets, not a fake.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentSeedImporterTest {

	private class FakeDataStore : DataStore<Preferences> {
		private val state = MutableStateFlow(emptyPreferences())
		override val data: Flow<Preferences> = state

		override suspend fun updateData(
			transform: suspend (t: Preferences) -> Preferences,
		): Preferences {
			val updated = transform(state.value)
			state.value = updated
			return updated
		}
	}

	private lateinit var database: ContentDatabase
	private lateinit var dataStore: FakeDataStore
	private lateinit var importer: ContentSeedImporter

	@Before
	fun setUp() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		database = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		dataStore = FakeDataStore()
		importer = ContentSeedImporter(context, database, dataStore)
	}

	@After
	fun tearDown() {
		database.close()
	}

	@Test
	fun `imports the real bundled seed with the expected row counts`() = runBlocking {
		val imported = importer.importIfNeeded()
		assertTrue("first import should run", imported)

		val readDao = database.contentReadDao()
		val answerKeyDao = database.contentAnswerKeyDao()

		assertEquals(5011, readDao.vocabularyCount())
		assertEquals(13, readDao.lessonCount())

		val levels = readDao.getLevels()
		assertEquals(1, levels.size)
		val units = levels.flatMap { readDao.getUnitsForLevel(it.id) }
		assertEquals(6, units.size)
		val lessons = units.flatMap { readDao.getLessonsForUnit(it.id) }
		assertEquals(13, lessons.size)
		val exercises = lessons.flatMap { readDao.getExercisesForLesson(it.id) }
		assertEquals(32, exercises.size)

		val options = exercises.sumOf { answerKeyDao.getOptions(it.id).size }
		val answers = exercises.sumOf { answerKeyDao.getAnswers(it.id).size }
		val hints = exercises.sumOf { answerKeyDao.getHints(it.id).size }
		assertEquals(79, options)
		assertEquals(20, answers)
		assertEquals(63, hints)

		// Spot-check one known row rather than trusting counts alone.
		val a1 = readDao.getVocabularyByLevel("A1")
		assertTrue(a1.any { it.headword == "about" && it.bnGloss.isNotBlank() })
	}

	@Test
	fun `re-importing after the version is already current is a no-op`() = runBlocking {
		assertTrue(importer.importIfNeeded())
		val countAfterFirst = database.contentReadDao().vocabularyCount()

		val ranAgain = importer.importIfNeeded()

		assertFalse("second call should be skipped by the version gate", ranAgain)
		assertEquals(countAfterFirst, database.contentReadDao().vocabularyCount())
	}
}
