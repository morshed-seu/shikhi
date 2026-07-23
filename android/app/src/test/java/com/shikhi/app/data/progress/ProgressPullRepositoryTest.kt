package com.shikhi.app.data.progress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.CompletedLessonEntry
import com.shikhi.app.data.api.dto.ProgressSnapshotResponse
import com.shikhi.app.data.api.dto.ReviewProgressEntry
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.api.dto.WordProgressEntry
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.db.ShikhiDatabase
import com.shikhi.app.data.outbox.OutboxRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * UO6 (docs/95-unified-offline-online-design.md §3.2): [ProgressPullRepository] against a real
 * in-memory [ShikhiDatabase] (mirrors [com.shikhi.app.data.auth.GuestRegistrationWorkerTest]'s
 * Robolectric+real-Room approach) — the pull-rebuild exercises real DAO delete+insert+transaction
 * behavior, not fakes. [ProgressApi]/[OutboxRepository]/[AuthRepository] are MockK-mocked, per
 * this codebase's Retrofit-layer convention.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgressPullRepositoryTest {

	private class FakeTokenStore : TokenStore {
		override val accessToken: StateFlow<String?> = MutableStateFlow(null)
		override suspend fun currentRefreshToken(): String? = null
		override suspend fun setSession(accessToken: String, refreshToken: String) = Unit
		override suspend fun clear() = Unit
		override suspend fun localGuestId(): String? = null
		override suspend fun setLocalGuestId(id: String) = Unit
		override suspend fun clearLocalGuestId() = Unit

		private var storedLastSyncedAt: Long? = null
		override suspend fun lastSyncedAt(): Long? = storedLastSyncedAt
		override suspend fun setLastSyncedAt(value: Long) { storedLastSyncedAt = value }
	}

	private lateinit var db: ShikhiDatabase
	private lateinit var progressApi: ProgressApi
	private lateinit var outbox: OutboxRepository
	private lateinit var authRepository: AuthRepository
	private lateinit var tokenStore: FakeTokenStore
	private lateinit var projection: StatsProjectionRepository

	private val userId = "server-user-1"
	private val user = User(id = userId, displayName = "Nusrat", uiLocale = "bn", isGuest = false)

	@Before
	fun setUp() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		db = Room.inMemoryDatabaseBuilder(context, ShikhiDatabase::class.java).allowMainThreadQueries().build()
		progressApi = mockk()
		outbox = mockk()
		authRepository = mockk()
		tokenStore = FakeTokenStore()
		projection = StatsProjectionRepository(db.localStatsProjectionDao(), db.outboxDao(), db.localLessonCompletionDao())
	}

	@After
	fun tearDown() {
		db.close()
	}

	private fun repository(): ProgressPullRepository = ProgressPullRepository(
		progressApi = dagger.Lazy { progressApi },
		outbox = outbox,
		db = db,
		projection = projection,
		authRepository = authRepository,
		tokenStore = tokenStore,
	)

	private fun snapshot(forUserId: String = userId) = ProgressSnapshotResponse(
		stats = Stats(hearts = 3, xp = 340, currentStreak = 6, longestStreak = 11, rank = 42, dailyGoal = 20, cefrLevel = "B1"),
		wordProgress = listOf(
			WordProgressEntry(
				vocabularyId = "v1",
				timesSeen = 5,
				timesCorrect = 4,
				timesWrong = 1,
				masteryScore = 3,
				lastWrongAt = "2026-07-20T10:00:00Z",
				lastSeenAt = "2026-07-22T10:00:00Z",
			),
		),
		reviewProgress = listOf(
			ReviewProgressEntry(
				vocabularyId = "v2",
				reviewStage = 2,
				dueAt = "2026-07-25T00:00:00Z",
				lastReviewedAt = "2026-07-21T00:00:00Z",
				reviewCount = 4,
				successfulReviews = 3,
				failedReviews = 1,
				failureStreak = 0,
				lastFailureAt = null,
			),
		),
		completedLessons = listOf(
			CompletedLessonEntry(lessonId = "lesson-1", contentVersionId = "cv-1", score = 90),
		),
		serverTime = "2026-07-23T09:00:00Z",
	)

	private fun activeSession() {
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(user))
	}

	@Test
	fun `rebuild from snapshot populates all three tables and reconciles stats`() = runBlocking {
		activeSession()
		coEvery { outbox.flush() } returns true
		val snap = snapshot()
		coEvery { progressApi.snapshot() } returns snap

		val ok = repository().pull()

		assertTrue(ok)
		val word = db.wordProgressDao().getWordProgress(userId, "v1")
		assertEquals(3, word?.masteryScore)
		assertEquals(5, word?.timesSeen)

		val review = db.wordProgressDao().getReviewProgress(userId, "v2")
		assertEquals(2, review?.reviewStage)
		assertEquals(4, review?.reviewCount)

		// contentVersionId is stored under the local "" sentinel, not the server's "cv-1" — see
		// ProgressPullRepository's CONTENT_VERSION_ID comment: LocalLessonSource's own
		// "already completed" gate always queries "", so a pulled row must live there too.
		val completion = db.localLessonCompletionDao().get(userId, "lesson-1", "")
		assertEquals(-1L, completion?.firstCompletionEventId)
		assertNull(db.localLessonCompletionDao().get(userId, "lesson-1", "cv-1"))

		val statsRow = db.localStatsProjectionDao().get(userId)
		assertEquals(340, statsRow?.baselineXp)
		assertEquals(3, statsRow?.hearts)
		assertEquals(6, statsRow?.currentStreak)
		assertEquals("B1", statsRow?.cefrLevel)
	}

	@Test
	fun `pull aborts when outbox can't be flushed and never calls snapshot`() = runBlocking {
		activeSession()
		coEvery { outbox.flush() } returns false

		val ok = repository().pull()

		assertTrue(!ok)
		coVerify(exactly = 0) { progressApi.snapshot() }
		assertNull(db.wordProgressDao().getWordProgress(userId, "v1"))
		assertNull(db.localStatsProjectionDao().get(userId))
	}

	@Test
	fun `reinstall rebuild populates everything from a fresh snapshot`() = runBlocking {
		activeSession()
		coEvery { outbox.flush() } returns true
		coEvery { progressApi.snapshot() } returns snapshot()

		assertNull("precondition: nothing local yet", db.wordProgressDao().getWordProgress(userId, "v1"))

		val ok = repository().pull()

		assertTrue(ok)
		assertTrue(db.wordProgressDao().getWordProgress(userId, "v1") != null)
		assertTrue(db.wordProgressDao().getReviewProgress(userId, "v2") != null)
		assertTrue(db.localLessonCompletionDao().get(userId, "lesson-1", "") != null)
	}

	@Test
	fun `LocalGuest session is a no-op — no flush, no snapshot call`() = runBlocking {
		every { authRepository.session } returns MutableStateFlow(SessionState.LocalGuest)

		val ok = repository().pull()

		assertTrue(ok)
		coVerify(exactly = 0) { progressApi.snapshot() }
		coVerify(exactly = 0) { outbox.flush() }
	}

	@Test
	fun `pull writes rows under the correct just-activated userId`() = runBlocking {
		val specificUser = User(id = "just-activated-42", displayName = null, uiLocale = "bn", isGuest = false)
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(specificUser))
		coEvery { outbox.flush() } returns true
		coEvery { progressApi.snapshot() } returns snapshot()

		val ok = repository().pull()

		assertTrue(ok)
		assertTrue(db.wordProgressDao().getWordProgress("just-activated-42", "v1") != null)
		assertNull(db.wordProgressDao().getWordProgress(userId, "v1"))
	}

	@Test
	fun `lastSyncedAt is persisted from snapshot serverTime after a successful pull`() = runBlocking {
		activeSession()
		coEvery { outbox.flush() } returns true
		val snap = snapshot()
		coEvery { progressApi.snapshot() } returns snap

		val ok = repository().pull()

		assertTrue(ok)
		assertEquals(Instant.parse(snap.serverTime).toEpochMilli(), tokenStore.lastSyncedAt())
	}
}
