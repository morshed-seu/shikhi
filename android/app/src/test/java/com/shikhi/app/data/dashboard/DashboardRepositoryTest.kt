package com.shikhi.app.data.dashboard

import com.shikhi.app.data.api.DashboardApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
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
		repository = DashboardRepository(
			dashboardApi = retrofit.create(DashboardApi::class.java),
			userApi = retrofit.create(UserApi::class.java),
			cache = cache,
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
