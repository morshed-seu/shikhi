package com.shikhi.app.data.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.api.dto.TokenPair
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.db.LocalPracticeSession
import com.shikhi.app.data.db.LocalReviewProgress
import com.shikhi.app.data.db.LocalStatsProjection
import com.shikhi.app.data.db.LocalWordProgress
import com.shikhi.app.data.db.ShikhiDatabase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * OG2 (docs/94-offline-guest-bootstrap-design.md §3.3, ADR-0014): [GuestRegistrationWorker]
 * against a real in-memory [ShikhiDatabase] (mirrors [com.shikhi.app.data.db.ShikhiDatabaseMigrationTest]'s
 * Robolectric+real-Room approach) — real registration re-keys all three tables atomically, and a
 * mid-transaction failure leaves every row at the old id (ADR-0014's core atomicity guarantee).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GuestRegistrationWorkerTest {

	private class FakeTokenStore(
		var refresh: String? = null,
		private var storedLocalGuestId: String? = null,
	) : TokenStore {
		private val _accessToken = MutableStateFlow<String?>(null)
		override val accessToken: StateFlow<String?> = _accessToken
		val persistedSessions = mutableListOf<Pair<String, String>>()
		var localGuestIdCleared = false
		// finding-4 test hook: lets a test simulate AuthRepository.activate() clearing/changing
		// localGuestId *between* doWork()'s two reads of it (the initial read at the top and the
		// re-check right before the rekey transaction) — a race cancelUniqueWork() alone cannot
		// prevent, since it never interrupts an already-executing worker instance. When armed, the
		// SECOND call to localGuestId() (and every call after) returns [changeAfterFirstReadTo]
		// instead of the stored value; the first call is unaffected.
		private var changeAfterFirstReadArmed = false
		private var changeAfterFirstReadTo: String? = null
		private var localGuestIdReads = 0

		fun changeLocalGuestIdAfterFirstReadTo(value: String?) {
			changeAfterFirstReadArmed = true
			changeAfterFirstReadTo = value
		}

		override suspend fun currentRefreshToken(): String? = refresh

		override suspend fun setSession(accessToken: String, refreshToken: String) {
			persistedSessions += accessToken to refreshToken
			refresh = refreshToken
			_accessToken.value = accessToken
		}

		override suspend fun clear() {
			refresh = null
			_accessToken.value = null
		}

		override suspend fun localGuestId(): String? {
			localGuestIdReads++
			if (changeAfterFirstReadArmed && localGuestIdReads > 1) return changeAfterFirstReadTo
			return storedLocalGuestId
		}

		override suspend fun setLocalGuestId(id: String) {
			storedLocalGuestId = id
		}

		override suspend fun clearLocalGuestId() {
			storedLocalGuestId = null
			localGuestIdCleared = true
		}
	}

	private lateinit var db: ShikhiDatabase
	private lateinit var tokenStore: FakeTokenStore
	private lateinit var authApi: AuthApi
	private lateinit var userApi: UserApi
	private lateinit var authRepository: AuthRepository

	private val oldId = "local-guest-1"
	private val newId = "server-user-1"

	@Before
	fun setUp() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		db = Room.inMemoryDatabaseBuilder(context, ShikhiDatabase::class.java).allowMainThreadQueries().build()
		tokenStore = FakeTokenStore(refresh = null, storedLocalGuestId = oldId)
		authApi = mockk()
		userApi = mockk()
		authRepository = mockk(relaxed = true)
	}

	@After
	fun tearDown() {
		db.close()
	}

	private fun worker(): GuestRegistrationWorker {
		val context = ApplicationProvider.getApplicationContext<Context>()
		val factory = object : WorkerFactory() {
			override fun createWorker(
				appContext: Context,
				workerClassName: String,
				workerParameters: WorkerParameters,
			): ListenableWorker = GuestRegistrationWorker(
				appContext,
				workerParameters,
				authApi,
				dagger.Lazy { userApi },
				tokenStore,
				db,
				authRepository,
			)
		}
		return TestListenableWorkerBuilder<GuestRegistrationWorker>(context)
			.setWorkerFactory(factory)
			.build()
	}

	private suspend fun seedRows() {
		db.wordProgressDao().upsert(LocalWordProgress(userId = oldId, vocabularyId = "v1", lastSeenAt = 0L))
		db.wordProgressDao().upsertReview(
			LocalReviewProgress(userId = oldId, vocabularyId = "v2", reviewStage = 1, dueAt = 0L),
		)
		db.localPracticeSessionDao().upsertSession(
			LocalPracticeSession(id = "s1", userId = oldId, cefrLevel = "A1", status = "IN_PROGRESS", startedAt = 0L),
		)
		// UO2: a guest's reconciled stats projection must survive the LocalGuest -> Active re-key
		// exactly like the mastery/review/session rows above.
		db.localStatsProjectionDao().upsert(
			LocalStatsProjection(
				userId = oldId,
				baselineXp = 30,
				hearts = 4,
				currentStreak = 2,
				longestStreak = 5,
				cefrLevel = "A2",
				lastActiveDate = null,
				rank = 10,
				dailyGoal = 20,
				updatedAt = 0L,
			),
		)
	}

	@Test
	fun `no stored localGuestId is a no-op success (already registered)`() = runBlocking {
		tokenStore = FakeTokenStore(refresh = "already-there", storedLocalGuestId = null)

		val result = worker().doWork()

		assertEquals(ListenableWorker.Result.success(), result)
		coVerify(exactly = 0) { authApi.guest(any()) }
	}

	@Test
	fun `successful registration re-keys all three tables atomically and persists the session`() = runBlocking {
		seedRows()
		coEvery { authApi.guest(any()) } returns TokenPair("access-1", "refresh-1", 3600)
		val serverUser = User(id = newId, isGuest = true)
		coEvery { userApi.me() } returns serverUser

		val result = worker().doWork()

		assertEquals(ListenableWorker.Result.success(), result)

		val wordProgress = db.wordProgressDao().getWordProgress(newId, "v1")
		assertNotNull("word progress must be re-keyed to the server userId", wordProgress)
		assertNull("no row should remain under the old localGuestId", db.wordProgressDao().getWordProgress(oldId, "v1"))

		val reviewProgress = db.wordProgressDao().getReviewProgress(newId, "v2")
		assertNotNull("review progress must be re-keyed to the server userId", reviewProgress)
		assertNull(db.wordProgressDao().getReviewProgress(oldId, "v2"))

		val session = db.localPracticeSessionDao().getSession("s1")
		assertEquals(newId, session?.userId)

		val statsProjection = db.localStatsProjectionDao().get(newId)
		assertNotNull("stats projection must be re-keyed to the server userId", statsProjection)
		assertEquals(30, statsProjection?.baselineXp)
		assertNull("no projection row should remain under the old localGuestId", db.localStatsProjectionDao().get(oldId))

		assertEquals(listOf("access-1" to "refresh-1"), tokenStore.persistedSessions)
		assertTrue("localGuestId must be cleared once registration completes", tokenStore.localGuestIdCleared)
		assertNull(tokenStore.localGuestId())

		// ADR-0014 finding-1 fix: the worker itself is the sole caller of the LocalGuest -> Active
		// transition, and only after the re-key + clearLocalGuestId() have both completed.
		coVerify(exactly = 1) { authRepository.completeGuestRegistration(serverUser) }
	}

	@Test
	fun `a mid-transaction failure leaves every row at the old id, no partial re-key`() = runBlocking {
		seedRows()
		// A pre-existing row already occupies (newId, "v2") in local_review_progress — the
		// rekeyReview UPDATE for vocabularyId="v2" will collide with this row's primary key
		// (userId, vocabularyId) and throw a SQLiteConstraintException partway through the
		// transaction, exercising ADR-0014's all-or-nothing guarantee.
		db.wordProgressDao().upsertReview(
			LocalReviewProgress(userId = newId, vocabularyId = "v2", reviewStage = 1, dueAt = 0L),
		)
		coEvery { authApi.guest(any()) } returns TokenPair("access-1", "refresh-1", 3600)
		coEvery { userApi.me() } returns User(id = newId, isGuest = true)

		val result = worker().doWork()

		assertEquals("a mid-transaction failure must retry, not succeed", ListenableWorker.Result.retry(), result)

		// The FIRST query in the transaction (word_progress) must have been rolled back even
		// though it ran and would have succeeded in isolation.
		assertNotNull("word progress must remain at the OLD id — no partial re-key", db.wordProgressDao().getWordProgress(oldId, "v1"))
		assertNull(db.wordProgressDao().getWordProgress(newId, "v1"))

		// The THIRD query (practice session) never ran at all, and must also be untouched.
		assertEquals(oldId, db.localPracticeSessionDao().getSession("s1")?.userId)

		// The stats projection rekey (the LAST query in the transaction) never ran either.
		assertNotNull("stats projection must remain at the OLD id — no partial re-key", db.localStatsProjectionDao().get(oldId))
		assertNull(db.localStatsProjectionDao().get(newId))

		assertTrue("localGuestId must NOT be cleared after a failed attempt", tokenStore.localGuestId() != null)
		assertTrue("the failed registration's tokens are still on disk (see class doc)", tokenStore.persistedSessions.isNotEmpty())
		coVerify(exactly = 0) { authRepository.completeGuestRegistration(any()) }
	}

	@Test
	fun `a retry after setSession already succeeded does not re-provision a second server guest`() = runBlocking {
		seedRows()
		// Simulates a process death between setSession and the rekey transaction on a PRIOR
		// attempt: a refresh token is already on disk, so this attempt must resume from userApi.me()
		// instead of calling authApi.guest() again (ADR-0014 §5: exactly one guest per device).
		tokenStore = FakeTokenStore(refresh = "existing-refresh", storedLocalGuestId = oldId)
		val serverUser = User(id = newId, isGuest = true)
		coEvery { userApi.me() } returns serverUser

		val result = worker().doWork()

		assertEquals(ListenableWorker.Result.success(), result)
		coVerify(exactly = 0) { authApi.guest(any()) }
		assertEquals(newId, db.localPracticeSessionDao().getSession("s1")?.userId)
		coVerify(exactly = 1) { authRepository.completeGuestRegistration(serverUser) }
	}

	@Test
	fun `an IOException from guest() retries without touching any table`() = runBlocking {
		seedRows()
		coEvery { authApi.guest(any()) } throws IOException()

		val result = worker().doWork()

		assertEquals(ListenableWorker.Result.retry(), result)
		assertEquals(oldId, db.localPracticeSessionDao().getSession("s1")?.userId)
		assertTrue(tokenStore.persistedSessions.isEmpty())
		coVerify(exactly = 0) { authRepository.completeGuestRegistration(any()) }
	}

	@Test
	fun `an HttpException from guest() also retries`() = runBlocking {
		seedRows()
		val serverError = HttpException(
			Response.error<Unit>(503, "".toResponseBody("application/json".toMediaType())),
		)
		coEvery { authApi.guest(any()) } throws serverError

		val result = worker().doWork()

		assertEquals(ListenableWorker.Result.retry(), result)
	}

	@Test
	fun `finding 4 - localGuestId changing between doWork start and the rekey step aborts without rekeying`() = runBlocking {
		seedRows()
		coEvery { authApi.guest(any()) } returns TokenPair("access-1", "refresh-1", 3600)
		coEvery { userApi.me() } returns User(id = newId, isGuest = true)

		// Simulates AuthRepository.activate() (e.g. GuestBanner's "sign in to existing account")
		// racing this in-flight execution: by the time doWork() re-reads localGuestId() right
		// before the rekey transaction, it has already been cleared out from under it — the
		// TOCTOU window cancelUniqueWork() alone cannot close (ADR-0014 finding-4).
		tokenStore.changeLocalGuestIdAfterFirstReadTo(null)

		val result = worker().doWork()

		assertEquals("a superseded local guest must still report success, not retry", ListenableWorker.Result.success(), result)
		assertEquals(
			"no rekey may happen once localGuestId no longer matches what doWork() started with",
			oldId,
			db.localPracticeSessionDao().getSession("s1")?.userId,
		)
		assertNotNull(
			"word progress must remain at the OLD id — the rekey step must never have run",
			db.wordProgressDao().getWordProgress(oldId, "v1"),
		)
		assertNull(db.wordProgressDao().getWordProgress(newId, "v1"))
		coVerify(exactly = 0) { authRepository.completeGuestRegistration(any()) }
	}
}
