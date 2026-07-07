package com.shikhi.app.data.outbox

import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.OutboxEventEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Pins the web-outbox parity rules (frontend/src/api/outbox.ts): idempotency keys are
 * minted once at enqueue and reused verbatim on every flush attempt; events survive a
 * failed flush; a successful batch clears exactly what was sent.
 */
class OutboxRepositoryTest {

	private class FakeOutboxDao : OutboxDao {
		val rows = mutableListOf<OutboxEventEntity>()
		private var nextId = 1L

		override suspend fun insert(event: OutboxEventEntity) {
			rows += event.copy(id = nextId++)
		}

		override suspend fun all(): List<OutboxEventEntity> = rows.sortedBy { it.id }

		override suspend fun deleteByIds(ids: List<Long>) {
			rows.removeAll { it.id in ids }
		}
	}

	private lateinit var server: MockWebServer
	private lateinit var dao: FakeOutboxDao
	private lateinit var outbox: OutboxRepository

	private val statsJson = """{"hearts":5,"xp":10}"""

	@Before
	fun setUp() {
		server = MockWebServer()
		server.start()
		dao = FakeOutboxDao()
		val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
		val api = Retrofit.Builder()
			.baseUrl(server.url("/v1/"))
			.client(OkHttpClient())
			.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
			.build()
			.create(ProgressApi::class.java)
		val workManager = io.mockk.mockk<androidx.work.WorkManager>(relaxed = true)
		outbox = OutboxRepository(dao, dagger.Lazy { api }, dagger.Lazy { workManager })
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	private fun enqueueLesson(score: Int) = runBlocking {
		outbox.enqueue(
			OutboxEventType.COMPLETE_LESSON,
			buildJsonObject {
				put("lessonId", "lesson-1")
				put("score", score)
			},
		)
	}

	@Test
	fun `empty outbox flushes true without any network call`() = runBlocking {
		assertTrue(outbox.flush())
		assertEquals(0, server.requestCount)
	}

	@Test
	fun `successful flush sends the batch and clears the outbox`() = runBlocking {
		enqueueLesson(score = 4)
		enqueueLesson(score = 5)
		server.enqueue(MockResponse().setBody(statsJson).addHeader("Content-Type", "application/json"))

		assertTrue(outbox.flush())
		assertTrue(dao.rows.isEmpty())

		val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
		val events = body["events"]!!.jsonArray
		assertEquals(2, events.size)
		val first = events[0].jsonObject
		assertEquals("COMPLETE_LESSON", first["type"]!!.jsonPrimitive.content)
		assertEquals("lesson-1", first["payload"]!!.jsonObject["lessonId"]!!.jsonPrimitive.content)
	}

	@Test
	fun `failed flush keeps events and reuses the same idempotency keys on retry`() = runBlocking {
		enqueueLesson(score = 3)
		val originalKey = dao.rows.single().idempotencyKey

		server.enqueue(MockResponse().setResponseCode(503))
		assertFalse(outbox.flush())
		assertEquals(1, dao.rows.size)
		assertEquals(originalKey, dao.rows.single().idempotencyKey)

		server.enqueue(MockResponse().setBody(statsJson).addHeader("Content-Type", "application/json"))
		assertTrue(outbox.flush())
		assertTrue(dao.rows.isEmpty())

		server.takeRequest() // failed attempt
		val retried = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
		val retriedKey = retried["events"]!!.jsonArray[0].jsonObject["idempotencyKey"]!!.jsonPrimitive.content
		assertEquals("retry must replay the SAME key so the server dedupes", originalKey, retriedKey)
	}
}
