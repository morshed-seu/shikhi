package com.shikhi.app.data.progress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shikhi.app.data.api.PracticeApi
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ShikhiDatabase
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UO3 (`~/.claude/plans/unified-offline-online/UO3.md`): pins the three local writes an offline
 * CEFR level change must perform — the durable projection row (UO2), the `"stats"`
 * `content_cache` blob that [com.shikhi.app.data.practice.LocalPracticeSource.cachedCefrLevel]
 * reads, and exactly one buffered `SET_LEVEL` outbox event — and that none of it depends on any
 * network call succeeding.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LevelRepositoryTest {

	private lateinit var db: ShikhiDatabase
	private lateinit var levelRepository: LevelRepository

	private val userId = "user-1"
	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	@Before
	fun setUp() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		db = Room.inMemoryDatabaseBuilder(context, ShikhiDatabase::class.java).allowMainThreadQueries().build()
		val statsProjectionRepository = StatsProjectionRepository(db.localStatsProjectionDao(), db.outboxDao())

		val authRepository = mockk<AuthRepository>()
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(User(id = userId)))
		val tokenStore = mockk<TokenStore>(relaxed = true)

		val outboxRepository = OutboxRepository(
			db.outboxDao(),
			dagger.Lazy { mockk<ProgressApi>(relaxed = true) },
			dagger.Lazy { mockk<PracticeApi>(relaxed = true) },
			dagger.Lazy { mockk<androidx.work.WorkManager>(relaxed = true) },
			db,
			statsProjectionRepository,
			authRepository,
			tokenStore,
		)

		levelRepository = LevelRepository(
			statsProjectionRepository,
			db.contentCacheDao(),
			outboxRepository,
			authRepository,
			tokenStore,
		)
	}

	@After
	fun tearDown() = db.close()

	@Test
	fun `setLevel seeds a fresh projection row when none existed yet`() = runBlocking {
		levelRepository.setLevel("B1")

		val row = db.localStatsProjectionDao().get(userId)
		assertNotNull(row)
		assertEquals("B1", row?.cefrLevel)
	}

	@Test
	fun `setLevel updates cefrLevel on an existing projection row without clobbering other fields`() = runBlocking {
		val statsProjectionRepository = StatsProjectionRepository(db.localStatsProjectionDao(), db.outboxDao())
		statsProjectionRepository.reconcile(
			userId,
			Stats(xp = 120, hearts = 3, currentStreak = 5, longestStreak = 9, rank = 42, dailyGoal = 20, cefrLevel = "A2"),
		)

		levelRepository.setLevel("B1")

		val row = db.localStatsProjectionDao().get(userId)
		assertEquals("B1", row?.cefrLevel)
		assertEquals("level change must not touch baselineXp", 120, row?.baselineXp)
		assertEquals(3, row?.hearts)
		assertEquals(5, row?.currentStreak)
		assertEquals(9, row?.longestStreak)
		assertEquals(42, row?.rank)
		assertEquals(20, row?.dailyGoal)
	}

	@Test
	fun `setLevel updates the stats cache blob while preserving other cached fields`() = runBlocking {
		db.contentCacheDao().put(
			CachedPayload(
				key = "stats",
				json = json.encodeToString(Stats.serializer(), Stats(xp = 80, hearts = 4, cefrLevel = "A2")),
				updatedAt = 0L,
			),
		)

		levelRepository.setLevel("B1")

		val cached = db.contentCacheDao().get("stats")
		assertNotNull(cached)
		val decoded = json.decodeFromString(Stats.serializer(), cached!!.json)
		assertEquals("B1", decoded.cefrLevel)
		assertEquals("cache write must preserve the rest of the cached Stats", 80, decoded.xp)
		assertEquals(4, decoded.hearts)
	}

	@Test
	fun `setLevel seeds a default stats cache blob when none was cached yet`() = runBlocking {
		levelRepository.setLevel("B1")

		val cached = db.contentCacheDao().get("stats")
		assertNotNull(cached)
		assertEquals("B1", json.decodeFromString(Stats.serializer(), cached!!.json).cefrLevel)
	}

	@Test
	fun `setLevel enqueues exactly one SET_LEVEL outbox event with the cefrLevel and changedAt payload`() = runBlocking {
		levelRepository.setLevel("B1")

		val events = db.outboxDao().all()
		assertEquals(1, events.size)
		val event = events.single()
		assertEquals(OutboxEventType.SET_LEVEL, event.type)
		val payload = json.parseToJsonElement(event.payloadJson).jsonObject
		assertEquals("B1", payload.getValue("cefrLevel").jsonPrimitive.content)
		assertNotNull(payload["changedAt"])
	}

	@Test
	fun `two consecutive setLevel calls converge to the last level with only two outbox events`() = runBlocking {
		levelRepository.setLevel("A2")
		levelRepository.setLevel("B1")

		val row = db.localStatsProjectionDao().get(userId)
		assertEquals("B1", row?.cefrLevel)
		val cached = db.contentCacheDao().get("stats")
		assertEquals("B1", json.decodeFromString(Stats.serializer(), cached!!.json).cefrLevel)
		assertEquals(2, db.outboxDao().all().size)
	}
}
