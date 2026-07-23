package com.shikhi.app.data.dashboard

import com.shikhi.app.data.api.DashboardApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.progress.StatsProjectionRepository
import io.mockk.coEvery
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
 * Gate MD3: [DashboardRepository] must follow the same network-first, Room-cache-fallback
 * shape as CachedContentRepository (MA4, NFR-AN4) — a good fetch refreshes the cache, a
 * failed one serves the last good payload — plus the E13 account actions (identities
 * soft-fail, export stays byte-faithful, delete/patch pass through untouched).
 */
class DashboardRepositoryTest {

	private class FakeContentCacheDao : ContentCacheDao {
		val rows = mutableMapOf<String, CachedPayload>()
		override suspend fun get(key: String): CachedPayload? = rows[key]
		override suspend fun put(payload: CachedPayload) {
			rows[payload.key] = payload
		}
	}

	private lateinit var server: MockWebServer
	private lateinit var cache: FakeContentCacheDao
	private lateinit var repository: DashboardRepository
	private lateinit var statsProjectionRepository: StatsProjectionRepository

	private val userId = "user-1"
	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	private val dashboardJson = """
		{"stats":{"hearts":5,"xp":10,"currentStreak":1,"longestStreak":2,"dailyGoal":20,"cefrLevel":"A1"},
		 "wordMastery":[{"cefrLevel":"A1","mastered":3,"total":10}],
		 "reviewDueCount":4,"lessonsCompleted":1,"practiceSessionsCompleted":2,
		 "totalAnswered":8,"totalCorrect":6}
	""".trimIndent()

	@Before
	fun setUp() {
		server = MockWebServer()
		server.start()
		cache = FakeContentCacheDao()
		val retrofit = Retrofit.Builder()
			.baseUrl(server.url("/v1/"))
			.client(OkHttpClient())
			.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
			.build()
		statsProjectionRepository = mockk()
		// Default: a no-op passthrough, so existing tests that don't care about the UO4 overlay
		// keep seeing the raw cached stats. The overlay-specific test below overrides this.
		coEvery { statsProjectionRepository.overlay(any(), any()) } answers { secondArg() }
		val authRepository = mockk<AuthRepository>()
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(User(id = userId)))
		repository = DashboardRepository(
			dashboardApi = retrofit.create(DashboardApi::class.java),
			userApi = retrofit.create(UserApi::class.java),
			cache = cache,
			statsProjectionRepository = statsProjectionRepository,
			authRepository = authRepository,
			tokenStore = mockk(relaxed = true),
		)
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun `a successful fetch refreshes the cache and is not marked from-cache`() = runBlocking {
		server.enqueue(MockResponse().setBody(dashboardJson).addHeader("Content-Type", "application/json"))

		val result = repository.dashboard()

		assertTrue(result != null && !result.fromCache)
		assertEquals(4, result!!.value.reviewDueCount)
		assertEquals(1, cache.rows.size)
	}

	@Test
	fun `a failed fetch with a cached payload serves it marked from-cache`() = runBlocking {
		server.enqueue(MockResponse().setBody(dashboardJson).addHeader("Content-Type", "application/json"))
		repository.dashboard() // primes the cache

		server.enqueue(MockResponse().setResponseCode(503))
		val result = repository.dashboard()

		assertTrue(result != null && result.fromCache)
		assertEquals(6, result!!.value.totalCorrect)
	}

	@Test
	fun `a cached snapshot's stats are overlaid with the live local projection`() = runBlocking {
		server.enqueue(MockResponse().setBody(dashboardJson).addHeader("Content-Type", "application/json"))
		repository.dashboard() // primes the cache

		val overlaidStats = Stats(hearts = 1, xp = 777, currentStreak = 9, longestStreak = 9, cefrLevel = "C1")
		coEvery { statsProjectionRepository.overlay(userId, any()) } returns overlaidStats

		server.enqueue(MockResponse().setResponseCode(503))
		val result = repository.dashboard()

		assertTrue(result != null && result.fromCache)
		assertEquals("the returned dashboard.stats must be the overlaid value, not the raw cached one", overlaidStats, result!!.value.stats)
	}

	@Test
	fun `a failed fetch with nothing cached returns null`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(503))

		assertNull(repository.dashboard())
	}

	@Test
	fun `an identities failure soft-fails to an empty list`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(500))

		assertEquals(emptyList<Any>(), repository.identities())
	}

	@Test
	fun `identities pass through on success`() = runBlocking {
		server.enqueue(
			MockResponse()
				.setBody("""[{"provider":"EMAIL","verified":true,"maskedRef":"n***@example.com"}]""")
				.addHeader("Content-Type", "application/json"),
		)

		val identities = repository.identities()

		assertEquals(1, identities.size)
		assertEquals("n***@example.com", identities.single().maskedRef)
	}

	@Test
	fun `export is byte-faithful, not re-encoded`() = runBlocking {
		// Deliberately odd key order/whitespace: a round-trip through the DTO layer would
		// normalize this, so byte-faithfulness means it must come back exactly as sent.
		val raw = """{"b":2,"a":1,   "nested":{"x":true}}"""
		server.enqueue(MockResponse().setBody(raw).addHeader("Content-Type", "application/json"))

		assertEquals(raw, repository.exportRaw())
	}

	@Test
	fun `delete calls DELETE me`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(204))

		repository.deleteAccount()

		val recorded = server.takeRequest()
		assertEquals("DELETE", recorded.method)
		assertTrue(recorded.path!!.endsWith("/me"))
	}

	@Test
	fun `updateProfile PATCHes me and returns the updated user`() = runBlocking {
		server.enqueue(
			MockResponse()
				.setBody("""{"id":"u1","displayName":"Nadia","uiLocale":"en","roles":[],"isGuest":false}""")
				.addHeader("Content-Type", "application/json"),
		)

		val updated = repository.updateProfile(displayName = "Nadia", uiLocale = "en")

		assertEquals("Nadia", updated.displayName)
		assertEquals("en", updated.uiLocale)
		val recorded = server.takeRequest()
		assertEquals("PATCH", recorded.method)
	}
}
