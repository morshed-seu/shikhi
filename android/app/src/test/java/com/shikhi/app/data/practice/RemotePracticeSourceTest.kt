package com.shikhi.app.data.practice

import com.shikhi.app.data.api.PracticeApi
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.PracticeExercise
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.OutboxEventEntity
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
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
 * [RemotePracticeSource]: happy-path parity with pre-OF4 behavior (unchanged online UX), plus the
 * new OF4 resilience — a recoverable `submitAnswer` failure (network/5xx) buffers a
 * [OutboxEventType.RETRY_PRACTICE_SUBMIT] outbox event carrying the exact original request instead
 * of silently losing the answer; a non-recoverable failure (4xx) does not buffer, since retrying it
 * verbatim would just fail again identically.
 */
class RemotePracticeSourceTest {

	private class FakeOutboxDao : OutboxDao {
		val rows = mutableListOf<OutboxEventEntity>()
		private var nextId = 1L
		override suspend fun insert(event: OutboxEventEntity) { rows += event.copy(id = nextId++) }
		override suspend fun all(): List<OutboxEventEntity> = rows.sortedBy { it.id }
		override suspend fun deleteByIds(ids: List<Long>) { rows.removeAll { it.id in ids } }
	}

	private lateinit var server: MockWebServer
	private lateinit var practiceApi: PracticeApi
	private lateinit var outboxDao: FakeOutboxDao
	private lateinit var outbox: OutboxRepository
	private lateinit var source: RemotePracticeSource

	private val exercise = PracticeExercise(id = "ex-1", type = "WORD_MEANING", prompt = Bilingual("What does X mean?", "X মানে কী?"))

	@Before
	fun setUp() {
		server = MockWebServer()
		server.start()
		val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
		val retrofit = Retrofit.Builder()
			.baseUrl(server.url("/v1/"))
			.client(OkHttpClient())
			.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
			.build()
		practiceApi = retrofit.create(PracticeApi::class.java)
		outboxDao = FakeOutboxDao()
		val workManager = io.mockk.mockk<androidx.work.WorkManager>(relaxed = true)
		outbox = OutboxRepository(
			outboxDao,
			dagger.Lazy { io.mockk.mockk<ProgressApi>(relaxed = true) },
			dagger.Lazy { practiceApi },
			dagger.Lazy { workManager },
		)
		source = RemotePracticeSource(practiceApi, outbox)
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	@Test
	fun `a successful grade returns the server verdict and buffers nothing`() = runBlocking {
		server.enqueue(
			MockResponse()
				.setBody("""{"verdict":{"correct":true},"stats":{"hearts":5}}""")
				.addHeader("Content-Type", "application/json"),
		)

		val outcome = source.grade("session-1", exercise, buildJsonObject { put("selectedOptionId", "opt-1") })

		assertTrue(outcome.verdict.correct)
		assertEquals(5, outcome.hearts)
		assertTrue(outboxDao.rows.isEmpty())
	}

	@Test
	fun `a 5xx failure buffers a RETRY_PRACTICE_SUBMIT event with the exact original request`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(503))

		val answer = buildJsonObject { put("selectedOptionId", "opt-1") }
		val outcome = source.grade("session-1", exercise, answer)

		assertFalse(outcome.verdict.correct)
		assertEquals(1, outboxDao.rows.size)
		val event = outboxDao.rows.single()
		assertEquals(OutboxEventType.RETRY_PRACTICE_SUBMIT, event.type)
		val payload = Json.parseToJsonElement(event.payloadJson).jsonObject
		assertEquals("session-1", payload["sessionId"]!!.jsonPrimitive.content)
		assertEquals("ex-1", payload["exerciseId"]!!.jsonPrimitive.content)
		assertEquals("opt-1", payload["answer"]!!.jsonObject["selectedOptionId"]!!.jsonPrimitive.content)
		assertTrue("an idempotency key must be present so the eventual retry dedupes", payload["idempotencyKey"]!!.jsonPrimitive.content.isNotBlank())
	}

	@Test
	fun `a connection failure (IOException) also buffers a retry event`() = runBlocking {
		server.shutdown() // any request now fails with a ConnectException (an IOException)

		val outcome = source.grade("session-1", exercise, buildJsonObject { put("selectedOptionId", "opt-1") })

		assertFalse(outcome.verdict.correct)
		assertEquals(1, outboxDao.rows.size)
		assertEquals(OutboxEventType.RETRY_PRACTICE_SUBMIT, outboxDao.rows.single().type)
	}

	@Test
	fun `a 4xx failure does not buffer a retry (retrying it verbatim would just fail again)`() = runBlocking {
		server.enqueue(MockResponse().setResponseCode(409))

		val outcome = source.grade("session-1", exercise, buildJsonObject { put("selectedOptionId", "opt-1") })

		assertFalse(outcome.verdict.correct)
		assertTrue(outboxDao.rows.isEmpty())
	}

	@Test
	fun `start, nextRound, and complete delegate directly to PracticeApi unchanged`() = runBlocking {
		server.enqueue(
			MockResponse()
				.setBody("""{"sessionId":"s-1","round":1,"cefrLevel":"A1","levelUpEligible":false,"exercises":[]}""")
				.addHeader("Content-Type", "application/json"),
		)
		val round = source.start()
		assertEquals("s-1", round.sessionId)

		server.enqueue(
			MockResponse()
				.setBody("""{"sessionId":"s-1","round":2,"cefrLevel":"A1","levelUpEligible":false,"exercises":[]}""")
				.addHeader("Content-Type", "application/json"),
		)
		val round2 = source.nextRound("s-1")
		assertEquals(2, round2.round)

		server.enqueue(
			MockResponse()
				.setBody("""{"correctCount":3,"totalCount":10,"roundsPlayed":2,"xpEarned":30,"levelUpEligible":false,"stats":{"hearts":5}}""")
				.addHeader("Content-Type", "application/json"),
		)
		val result = source.complete("s-1")
		assertEquals(3, result.correctCount)
	}
}
