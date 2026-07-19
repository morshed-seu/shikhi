package com.shikhi.app.data.outbox

import com.shikhi.app.data.api.PracticeApi
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
 *
 * OF4 additions: [OutboxEventType.RETRY_PRACTICE_SUBMIT] events (buffered by
 * [com.shikhi.app.data.practice.RemotePracticeSource] on a recoverable `submitAnswer` failure)
 * replay individually against [PracticeApi.submitAnswer] instead of the generic `/progress/sync`
 * batch — see that constant's doc for why.
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
	private val answerResultJson = """{"verdict":{"correct":true},"stats":{"hearts":5}}"""

	@Before
	fun setUp() {
		server = MockWebServer()
		server.start()
		dao = FakeOutboxDao()
		val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
		val retrofit = Retrofit.Builder()
			.baseUrl(server.url("/v1/"))
			.client(OkHttpClient())
			.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
			.build()
		val progressApi = retrofit.create(ProgressApi::class.java)
		val practiceApi = retrofit.create(PracticeApi::class.java)
		val workManager = io.mockk.mockk<androidx.work.WorkManager>(relaxed = true)
		outbox = OutboxRepository(dao, dagger.Lazy { progressApi }, dagger.Lazy { practiceApi }, dagger.Lazy { workManager })
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

	@Test
	fun `PRACTICE_ANSWER events flow through the generic sync batch like COMPLETE_LESSON`() = runBlocking {
		outbox.enqueue(
			OutboxEventType.PRACTICE_ANSWER,
			buildJsonObject {
				put("vocabularyId", "vocab-1")
				put("correct", true)
				put("answeredAt", "2026-07-18T10:15:00Z")
			},
		)
		server.enqueue(MockResponse().setBody(statsJson).addHeader("Content-Type", "application/json"))

		assertTrue(outbox.flush())
		assertTrue(dao.rows.isEmpty())

		val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
		val event = body["events"]!!.jsonArray[0].jsonObject
		assertEquals("PRACTICE_ANSWER", event["type"]!!.jsonPrimitive.content)
		assertEquals("vocab-1", event["payload"]!!.jsonObject["vocabularyId"]!!.jsonPrimitive.content)
		assertEquals(true, event["payload"]!!.jsonObject["correct"]!!.jsonPrimitive.content.toBoolean())
	}

	@Test
	fun `RETRY_PRACTICE_SUBMIT events replay individually against PracticeApi submitAnswer, not the sync batch`() = runBlocking {
		outbox.enqueue(
			OutboxEventType.RETRY_PRACTICE_SUBMIT,
			buildJsonObject {
				put("sessionId", "session-1")
				put("exerciseId", "exercise-1")
				put("idempotencyKey", "idem-1")
				put("answer", buildJsonObject { put("selectedOptionId", "opt-1") })
			},
		)
		server.enqueue(MockResponse().setBody(answerResultJson).addHeader("Content-Type", "application/json"))

		assertTrue(outbox.flush())
		assertTrue(dao.rows.isEmpty())
		assertEquals(1, server.requestCount)

		val request = server.takeRequest()
		assertTrue(request.path!!.endsWith("/practice/sessions/session-1/answers"))
		val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
		assertEquals("idem-1", body["idempotencyKey"]!!.jsonPrimitive.content)
		assertEquals("exercise-1", body["exerciseId"]!!.jsonPrimitive.content)
		assertEquals("opt-1", body["answer"]!!.jsonObject["selectedOptionId"]!!.jsonPrimitive.content)
	}

	@Test
	fun `a failed RETRY_PRACTICE_SUBMIT is kept while an unrelated successful sync batch still clears`() = runBlocking {
		outbox.enqueue(
			OutboxEventType.RETRY_PRACTICE_SUBMIT,
			buildJsonObject {
				put("sessionId", "session-1")
				put("exerciseId", "exercise-1")
				put("idempotencyKey", "idem-1")
				put("answer", buildJsonObject { put("selectedOptionId", "opt-1") })
			},
		)
		enqueueLesson(score = 2)

		server.enqueue(MockResponse().setResponseCode(503)) // the retry fails
		server.enqueue(MockResponse().setBody(statsJson).addHeader("Content-Type", "application/json")) // the sync batch succeeds

		assertFalse(outbox.flush())
		assertEquals(1, dao.rows.size)
		assertEquals(OutboxEventType.RETRY_PRACTICE_SUBMIT, dao.rows.single().type)
	}
}
