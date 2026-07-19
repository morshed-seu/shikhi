package com.shikhi.app.data.auth

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.api.dto.RefreshRequest
import com.shikhi.app.data.api.dto.TokenPair
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.connectivity.ConnectivityChecker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * GF5: covers [AuthRepository]'s GF1 silent-guest-reprovisioning paths — bootstrap with no
 * stored session, the offline/backend-unreachable fallback to [SessionState.GuestUnavailable],
 * the manual retry, logout landing back on a guest (not a login wall), the dead-refresh-token
 * re-guest path, and the still-unchanged offline-stored-session path.
 *
 * OG1 additionally covers the local-first bootstrap decision order (no refresh token): a stored
 * [SessionState.LocalGuest] resumes locally with no network attempt, a true cold start offline
 * mints and persists a `localGuestId`, and the online-at-launch fast path to [SessionState.Active]
 * is unchanged.
 */
class AuthRepositoryTest {

	private val dispatcher = StandardTestDispatcher()

	private class FakeTokenStore(access: String?, private var refresh: String?, private var localGuestId: String? = null) : TokenStore {
		private val _accessToken = MutableStateFlow(access)
		override val accessToken: StateFlow<String?> = _accessToken
		var cleared = false

		override suspend fun currentRefreshToken(): String? = refresh

		override suspend fun setSession(accessToken: String, refreshToken: String) {
			refresh = refreshToken
			_accessToken.value = accessToken
		}

		override suspend fun clear() {
			cleared = true
			refresh = null
			_accessToken.value = null
		}

		override suspend fun localGuestId(): String? = localGuestId

		override suspend fun setLocalGuestId(id: String) {
			localGuestId = id
		}

		override suspend fun clearLocalGuestId() {
			localGuestId = null
		}
	}

	private fun repository(
		authApi: AuthApi,
		userApi: UserApi,
		tokenStore: TokenStore,
		online: Boolean = true,
		// A single fixed instance (not a per-call `dagger.Lazy { mockk(...) }`) so tests can
		// verify calls made through AuthRepository's own `workManager.get()` — finding
		// 2/3 tests need to observe enqueueUniqueWork/cancelUniqueWork on the SAME mock.
		workManager: WorkManager = mockk(relaxed = true),
	): AuthRepository = AuthRepository(
		authApi = authApi,
		userApi = { userApi },
		tokenStore = tokenStore,
		connectivity = mockk<ConnectivityChecker>(relaxed = true) { every { isOnline() } returns online },
		workManager = dagger.Lazy { workManager },
		appScope = CoroutineScope(dispatcher),
	)

	@Before
	fun setUp() = Dispatchers.setMain(dispatcher)

	@After
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun `no stored session auto-provisions a guest`() = runTest(dispatcher) {
		val guest = User(id = "g1", displayName = null, uiLocale = "bn", isGuest = true)
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)
		coEvery { authApi.guest(any()) } returns TokenPair("at", "rt", 3600)
		coEvery { userApi.me() } returns guest

		val repo = repository(authApi, userApi, FakeTokenStore(null, null))
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()

		val session = repo.session.value
		assertTrue(session is SessionState.Active)
		assertTrue((session as SessionState.Active).user.isGuest)
	}

	@Test
	fun `repeated IOException from guest() ends in GuestUnavailable, then retry succeeds`() = runTest(dispatcher) {
		val guest = User(id = "g1", displayName = null, uiLocale = "bn", isGuest = true)
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)
		coEvery { authApi.guest(any()) } throws IOException()
		coEvery { userApi.me() } returns guest

		val repo = repository(authApi, userApi, FakeTokenStore(null, null))
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()

		assertEquals(SessionState.GuestUnavailable, repo.session.value)

		coEvery { authApi.guest(any()) } returns TokenPair("at", "rt", 3600)
		repo.retryGuestProvisioning()
		dispatcher.scheduler.advanceUntilIdle()

		val session = repo.session.value
		assertTrue(session is SessionState.Active)
		assertTrue((session as SessionState.Active).user.isGuest)
	}

	@Test
	fun `OG1 no refresh token, no localGuestId, offline ends in LocalGuest with a localGuestId persisted`() = runTest(dispatcher) {
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)

		val tokenStore = FakeTokenStore(null, null)
		val repo = repository(authApi, userApi, tokenStore, online = false)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()

		// OG1 supersedes GF5's old "offline at bootstrap -> GuestUnavailable" expectation for this
		// exact scenario (no stored refresh token, no localGuestId): the app must never sit on a
		// retry screen when it can be fully usable offline instead.
		assertEquals(SessionState.LocalGuest, repo.session.value)
		assertTrue("a localGuestId must be minted and persisted", tokenStore.localGuestId() != null)
		coVerify(exactly = 0) { authApi.guest(any()) }
	}

	@Test
	fun `OG1 no refresh token, localGuestId already stored, immediately LocalGuest with no network call`() = runTest(dispatcher) {
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)

		val tokenStore = FakeTokenStore(null, null, localGuestId = "existing-local-id")
		// online = true here too: a stored localGuestId always wins over the network fast-path,
		// per design doc §3.2 step 2 — it means a previous local-only launch already happened.
		val repo = repository(authApi, userApi, tokenStore, online = true)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()

		assertEquals(SessionState.LocalGuest, repo.session.value)
		assertEquals("existing-local-id", tokenStore.localGuestId())
		coVerify(exactly = 0) { authApi.guest(any()) }
	}

	@Test
	fun `OG1 no refresh token, online, no localGuestId keeps the existing fast path to Active`() = runTest(dispatcher) {
		val guest = User(id = "g1", displayName = null, uiLocale = "bn", isGuest = true)
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)
		coEvery { authApi.guest(any()) } returns TokenPair("at", "rt", 3600)
		coEvery { userApi.me() } returns guest

		val tokenStore = FakeTokenStore(null, null)
		val repo = repository(authApi, userApi, tokenStore, online = true)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()

		val session = repo.session.value
		assertTrue("online at launch must still reach Active directly, not LocalGuest", session is SessionState.Active)
		assertTrue((session as SessionState.Active).user.isGuest)
		assertEquals(null, tokenStore.localGuestId())
	}

	@Test
	fun `logout on an active guest session re-provisions a guest instead of logging out`() = runTest(dispatcher) {
		val guest = User(id = "g1", displayName = null, uiLocale = "bn", isGuest = true)
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)
		coEvery { authApi.guest(any()) } returns TokenPair("at2", "rt2", 3600)
		coEvery { userApi.me() } returns guest

		val tokenStore = FakeTokenStore(null, null)
		val repo = repository(authApi, userApi, tokenStore)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()
		assertTrue(repo.session.value is SessionState.Active)

		repo.logout()
		dispatcher.scheduler.advanceUntilIdle()

		assertTrue(
			"logout must land back on a guest session, not a login wall",
			repo.session.value is SessionState.Active,
		)
		assertTrue(tokenStore.cleared)
	}

	@Test
	fun `a dead stored refresh token re-provisions a guest instead of LoggedOut`() = runTest(dispatcher) {
		val guest = User(id = "g1", displayName = null, uiLocale = "bn", isGuest = true)
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)
		val notFound = HttpException(Response.error<Unit>(404, "".toResponseBody("application/json".toMediaType())))
		coEvery { authApi.refresh(RefreshRequest("rt")) } throws notFound
		coEvery { authApi.guest(any()) } returns TokenPair("at", "rt2", 3600)
		coEvery { userApi.me() } returns guest

		val tokenStore = FakeTokenStore(null, "rt")
		val repo = repository(authApi, userApi, tokenStore)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()

		assertTrue(repo.session.value is SessionState.Active)
		assertTrue(tokenStore.cleared)
	}

	@Test
	fun `an offline stored session stays Unavailable (unchanged by GF1)`() = runTest(dispatcher) {
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)
		coEvery { authApi.refresh(RefreshRequest("rt")) } throws IOException()

		val tokenStore = FakeTokenStore(null, "rt")
		val repo = repository(authApi, userApi, tokenStore)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()

		assertEquals(SessionState.Unavailable, repo.session.value)
		assertFalse(tokenStore.cleared)
		coVerify(exactly = 0) { authApi.guest(any()) }
	}

	@Test
	fun `finding 1 - a bare token appearing does not flip LocalGuest to Active on its own`() = runTest(dispatcher) {
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)

		val tokenStore = FakeTokenStore(null, null, localGuestId = "local-1")
		val repo = repository(authApi, userApi, tokenStore, online = false)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()
		assertEquals(SessionState.LocalGuest, repo.session.value)

		// Simulates GuestRegistrationWorker calling tokenStore.setSession(...) BEFORE its own
		// rekey transaction + completeGuestRegistration() call — the exact race finding 1
		// describes. Without the worker itself calling completeGuestRegistration, the session
		// must stay LocalGuest.
		tokenStore.setSession("at", "rt")
		dispatcher.scheduler.advanceUntilIdle()

		assertEquals(
			"a bare token must not race the worker's own rekey by flipping session state itself",
			SessionState.LocalGuest,
			repo.session.value,
		)
		coVerify(exactly = 0) { userApi.me() }
	}

	@Test
	fun `finding 1 - completeGuestRegistration is the only path from LocalGuest to Active`() = runTest(dispatcher) {
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)

		val tokenStore = FakeTokenStore(null, null, localGuestId = "local-1")
		val repo = repository(authApi, userApi, tokenStore, online = false)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()
		assertEquals(SessionState.LocalGuest, repo.session.value)

		val serverUser = User(id = "server-1", displayName = null, uiLocale = "bn", isGuest = true)
		repo.completeGuestRegistration(serverUser)

		val session = repo.session.value
		assertTrue(session is SessionState.Active)
		assertEquals(serverUser, (session as SessionState.Active).user)
	}

	@Test
	fun `finding 2 - refresh token present with a leftover localGuestId still reaches Active and re-enqueues registration`() =
		runTest(dispatcher) {
			val user = User(id = "u1", displayName = null, uiLocale = "bn", isGuest = false)
			val authApi = mockk<AuthApi>(relaxed = true)
			val userApi = mockk<UserApi>(relaxed = true)
			coEvery { authApi.refresh(RefreshRequest("rt")) } returns TokenPair("at", "rt2", 3600)
			coEvery { userApi.me() } returns user

			val tokenStore = FakeTokenStore(null, "rt", localGuestId = "leftover-local-id")
			val workManager = mockk<WorkManager>(relaxed = true)
			val repo = repository(authApi, userApi, tokenStore, workManager = workManager)
			repo.bootstrap()
			dispatcher.scheduler.advanceUntilIdle()

			assertTrue(
				"a leftover localGuestId must not prevent the ordinary refresh -> Active path",
				repo.session.value is SessionState.Active,
			)
			verify(
				exactly = 1,
			) { workManager.enqueueUniqueWork(any<String>(), ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>()) }
		}

	@Test
	fun `finding 2 - refresh token present with no localGuestId does not re-enqueue (common case unaffected)`() = runTest(dispatcher) {
		val guest = User(id = "g1", displayName = null, uiLocale = "bn", isGuest = true)
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)
		coEvery { authApi.refresh(RefreshRequest("rt")) } returns TokenPair("at", "rt2", 3600)
		coEvery { userApi.me() } returns guest

		val tokenStore = FakeTokenStore(null, "rt")
		val workManager = mockk<WorkManager>(relaxed = true)
		val repo = repository(authApi, userApi, tokenStore, workManager = workManager)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()

		assertTrue(repo.session.value is SessionState.Active)
		verify(exactly = 0) {
			workManager.enqueueUniqueWork(any<String>(), any<ExistingWorkPolicy>(), any<OneTimeWorkRequest>())
		}
	}

	@Test
	fun `finding 3 - logging out from LocalGuest cancels pending work, clears the old id, and stays usable offline with a fresh id`() =
		runTest(dispatcher) {
			val authApi = mockk<AuthApi>(relaxed = true)
			val userApi = mockk<UserApi>(relaxed = true)
			val tokenStore = FakeTokenStore(null, null)
			val workManager = mockk<WorkManager>(relaxed = true)
			val repo = repository(authApi, userApi, tokenStore, online = false, workManager = workManager)

			repo.bootstrap()
			dispatcher.scheduler.advanceUntilIdle()
			assertEquals(SessionState.LocalGuest, repo.session.value)
			val originalLocalGuestId = tokenStore.localGuestId()
			assertTrue(originalLocalGuestId != null)

			repo.logout()
			dispatcher.scheduler.advanceUntilIdle()

			verify(exactly = 1) { workManager.cancelUniqueWork(GuestRegistrationWorker.UNIQUE_NAME) }
			assertEquals(
				"logout offline must not strand the user on ConnectingScreen — it lands on a fresh LocalGuest",
				SessionState.LocalGuest,
				repo.session.value,
			)
			val newLocalGuestId = tokenStore.localGuestId()
			assertTrue(
				"a new, different localGuestId must be minted — the old one is abandoned intentionally",
				newLocalGuestId != null && newLocalGuestId != originalLocalGuestId,
			)
		}

	@Test
	fun `finding 4 - login from a LocalGuest session cancels pending registration and clears the old localGuestId`() =
		runTest(dispatcher) {
			val authApi = mockk<AuthApi>(relaxed = true)
			val userApi = mockk<UserApi>(relaxed = true)
			val tokenStore = FakeTokenStore(null, null)
			val workManager = mockk<WorkManager>(relaxed = true)
			val repo = repository(authApi, userApi, tokenStore, online = false, workManager = workManager)

			repo.bootstrap()
			dispatcher.scheduler.advanceUntilIdle()
			assertEquals(SessionState.LocalGuest, repo.session.value)
			assertTrue("a localGuestId must be minted for the local phase", tokenStore.localGuestId() != null)

			// GuestBanner's "sign in to existing account" flow (confirmDiscardAndSignIn): the user
			// discards the LocalGuest's progress and logs into a different, pre-existing account.
			val accountB = User(id = "account-b", displayName = "B", uiLocale = "bn", isGuest = false)
			coEvery { authApi.login(any()) } returns TokenPair("at-b", "rt-b", 3600)
			coEvery { userApi.me() } returns accountB

			repo.login("b@example.com", "password")
			dispatcher.scheduler.advanceUntilIdle()

			// activate() must cancel the pending GuestRegistrationWorker before it can land later
			// and silently rekey the discarded guest's rows onto the newly-activated account.
			verify(exactly = 1) { workManager.cancelUniqueWork(GuestRegistrationWorker.UNIQUE_NAME) }
			assertEquals(
				"the discarded LocalGuest's id must be cleared so a still-in-flight worker execution's " +
					"own re-check (see GuestRegistrationWorkerTest) also bails out",
				null,
				tokenStore.localGuestId(),
			)
			val session = repo.session.value
			assertTrue(session is SessionState.Active)
			assertEquals(accountB, (session as SessionState.Active).user)
		}
}
