package com.shikhi.app.data.content

import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.content.db.LocalExercise
import com.shikhi.app.data.content.db.LocalLesson
import com.shikhi.app.data.content.db.LocalLevel
import com.shikhi.app.data.content.db.LocalUnit
import com.shikhi.app.data.content.db.LocalVocabulary
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.progress.StatsProjectionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Gate OF2: [CachedContentRepository.vocabulary] and [CachedContentRepository.curriculum] now
 * read the bundled [ContentReadDao] with zero network calls (docs/93-offline-learning-design.md
 * §3.2/§3.3) — no `VocabularyApi`/`ContentApi` involved at all, so these are pure DAO-fake
 * tests, no MockWebServer. [CachedContentRepository.stats] is unaffected (still network-first,
 * Room-cache-fallback) and keeps the MockWebServer-based test shape from
 * `DashboardRepositoryTest`.
 */
class CachedContentRepositoryTest {

	/** In-memory fake — exercises the read paths [CachedContentRepository] actually calls. */
	private class FakeContentReadDao : ContentReadDao {
		val vocabulary = mutableListOf<LocalVocabulary>()
		val levels = mutableListOf<LocalLevel>()
		val units = mutableListOf<LocalUnit>()
		val lessons = mutableListOf<LocalLesson>()

		override suspend fun getVocabularyByLevel(level: String): List<LocalVocabulary> =
			vocabulary.filter { it.cefrLevel == level }.sortedBy { it.ordinal }

		override suspend fun getLevels(): List<LocalLevel> = levels.sortedBy { it.ordinal }

		override suspend fun getUnitsForLevel(levelId: String): List<LocalUnit> =
			units.filter { it.levelId == levelId }.sortedBy { it.ordinal }

		override suspend fun getLessonsForUnit(unitId: String): List<LocalLesson> =
			lessons.filter { it.unitId == unitId }.sortedBy { it.ordinal }

		override suspend fun getLesson(lessonId: String): LocalLesson? = lessons.find { it.id == lessonId }

		override suspend fun getExercisesForLesson(lessonId: String): List<LocalExercise> = emptyList()

		override suspend fun insertVocabulary(rows: List<LocalVocabulary>) {
			vocabulary += rows
		}

		override suspend fun insertLevels(rows: List<LocalLevel>) {
			levels += rows
		}

		override suspend fun insertUnits(rows: List<LocalUnit>) {
			units += rows
		}

		override suspend fun insertLessons(rows: List<LocalLesson>) {
			lessons += rows
		}

		override suspend fun insertExercises(rows: List<LocalExercise>) = Unit

		override suspend fun vocabularyCount(): Int = vocabulary.size

		override suspend fun lessonCount(): Int = lessons.size
	}

	private class FakeContentCacheDao : ContentCacheDao {
		val rows = mutableMapOf<String, CachedPayload>()
		override suspend fun get(key: String): CachedPayload? = rows[key]
		override suspend fun put(payload: CachedPayload) {
			rows[payload.key] = payload
		}
	}

	private fun vocab(id: String, headword: String, level: String, ordinal: Int) = LocalVocabulary(
		id = id,
		headword = headword,
		senseLabel = null,
		partOfSpeech = "noun",
		cefrLevel = level,
		bnGloss = "$headword-bn",
		exampleEn = "This is $headword.",
		exampleBn = null,
		ordinal = ordinal,
	)

	private lateinit var contentDao: FakeContentReadDao
	private lateinit var cache: FakeContentCacheDao
	private lateinit var statsProjectionRepository: StatsProjectionRepository
	private lateinit var authRepository: AuthRepository

	private val userId = "user-1"

	@Before
	fun setUp() {
		contentDao = FakeContentReadDao()
		cache = FakeContentCacheDao()
		statsProjectionRepository = mockk()
		// Default: a no-op passthrough, so existing tests that don't care about the UO4 overlay
		// keep seeing the raw cached Stats. Tests that DO care override this with their own
		// coEvery stub returning a distinguishable value.
		coEvery { statsProjectionRepository.overlay(any(), any()) } answers { secondArg() }
		authRepository = mockk()
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(User(id = userId)))
	}

	private fun repository(server: MockWebServer? = null): CachedContentRepository {
		val progressApi = if (server != null) {
			val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
			Retrofit.Builder()
				.baseUrl(server.url("/v1/"))
				.client(OkHttpClient())
				.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
				.build()
				.create(ProgressApi::class.java)
		} else {
			object : ProgressApi {
				override suspend fun stats() = throw UnsupportedOperationException()
				override suspend fun sync(body: com.shikhi.app.data.api.dto.SyncBatchRequest) =
					throw UnsupportedOperationException()
				override suspend fun setLevel(body: com.shikhi.app.data.api.dto.SetLevelRequest) =
					throw UnsupportedOperationException()
				override suspend fun snapshot() = throw UnsupportedOperationException()
			}
		}
		return CachedContentRepository(
			progressApi = progressApi,
			cache = cache,
			contentDao = contentDao,
			statsProjectionRepository = statsProjectionRepository,
			authRepository = authRepository,
			tokenStore = mockk(relaxed = true),
		)
	}

	@Test
	fun `vocabulary reads the bundled DAO and maps LocalVocabulary field-for-field`() = runBlocking {
		contentDao.vocabulary += vocab("v1", "apple", "A1", 0)
		contentDao.vocabulary += vocab("v2", "banana", "A1", 1)
		contentDao.vocabulary += vocab("v3", "cat", "A2", 0)

		val result = repository().vocabulary("A1")

		assertTrue(result != null && !result.fromCache)
		assertEquals(listOf("apple", "banana"), result!!.value.map { it.headword })
		val apple = result.value.first()
		assertEquals("v1", apple.id)
		assertEquals("noun", apple.partOfSpeech)
		assertEquals("apple-bn", apple.bnGloss)
		assertEquals("This is apple.", apple.exampleEn)
	}

	@Test
	fun `vocabulary returns null when the level has no rows (not yet seeded or genuinely empty)`() = runBlocking {
		val result = repository().vocabulary("A1")

		assertNull(result)
	}

	@Test
	fun `curriculum assembles levels units and lessons from the bundled DAO`() = runBlocking {
		contentDao.levels += LocalLevel(id = "lvl-a1", code = "A1", titleEn = "Beginner", titleBn = "বেসিক", ordinal = 0)
		contentDao.units += LocalUnit(id = "unit-1", levelId = "lvl-a1", code = "U1", titleEn = "Greetings", titleBn = "শুভেচ্ছা", ordinal = 0)
		contentDao.lessons += LocalLesson(id = "lesson-1", unitId = "unit-1", code = "L1", titleEn = "Hello", titleBn = "হ্যালো", ordinal = 0)

		val result = repository().curriculum()

		assertTrue(result != null && !result.fromCache)
		val level = result!!.value.levels.single()
		assertEquals("A1", level.code)
		assertEquals("Beginner", level.title.en)
		val unit = level.units.single()
		assertEquals("Greetings", unit.title.en)
		val lesson = unit.lessons.single()
		assertEquals("Hello", lesson.title.en)
		// Per-learner progress is a server-side overlay not carried by bundled content (§4.1).
		assertEquals("NOT_STARTED", lesson.status)
		assertEquals(false, lesson.locked)
	}

	@Test
	fun `curriculum returns null when nothing has been seeded yet`() = runBlocking {
		val result = repository().curriculum()

		assertNull(result)
	}

	@Test
	fun `stats is unaffected, network-first with a Room cache fallback`() = runBlocking {
		val server = MockWebServer()
		server.start()
		try {
			val statsJson = """{"hearts":5,"xp":10,"currentStreak":1,"longestStreak":2,"rank":0,"dailyGoal":20,"cefrLevel":"A1"}"""
			server.enqueue(MockResponse().setBody(statsJson).addHeader("Content-Type", "application/json"))

			val fresh = repository(server).stats()
			assertTrue(fresh != null && !fresh.fromCache)
			assertEquals(10, fresh!!.value.xp)
			assertEquals(1, cache.rows.size)

			server.enqueue(MockResponse().setResponseCode(503))
			val stale = repository(server).stats()
			assertTrue(stale != null && stale.fromCache)
			assertEquals(10, stale!!.value.xp)
		} finally {
			server.shutdown()
		}
	}

	// ---- UO4: overlaying the live local projection onto a cached stats() response -----------

	@Test
	fun `a cached stats result is overlaid with the live local projection`() = runBlocking {
		val server = MockWebServer()
		server.start()
		try {
			val statsJson = """{"hearts":5,"xp":10,"currentStreak":1,"longestStreak":2,"rank":0,"dailyGoal":20,"cefrLevel":"A1"}"""
			server.enqueue(MockResponse().setBody(statsJson).addHeader("Content-Type", "application/json"))
			val repo = repository(server)
			repo.stats() // primes the cache

			val overlaidValue = Stats(hearts = 3, xp = 999, currentStreak = 7, longestStreak = 7, cefrLevel = "B1")
			coEvery { statsProjectionRepository.overlay(userId, any()) } returns overlaidValue

			server.enqueue(MockResponse().setResponseCode(503))
			val stale = repo.stats()

			assertTrue(stale != null && stale.fromCache)
			assertEquals(overlaidValue, stale!!.value)
		} finally {
			server.shutdown()
		}
	}

	@Test
	fun `a fresh (non-cached) stats result is not overlaid`() = runBlocking {
		val server = MockWebServer()
		server.start()
		try {
			val statsJson = """{"hearts":5,"xp":10,"currentStreak":1,"longestStreak":2,"rank":0,"dailyGoal":20,"cefrLevel":"A1"}"""
			server.enqueue(MockResponse().setBody(statsJson).addHeader("Content-Type", "application/json"))

			val fresh = repository(server).stats()

			assertTrue(fresh != null && !fresh.fromCache)
			coVerify(exactly = 0) { statsProjectionRepository.overlay(any(), any()) }
		} finally {
			server.shutdown()
		}
	}
}
