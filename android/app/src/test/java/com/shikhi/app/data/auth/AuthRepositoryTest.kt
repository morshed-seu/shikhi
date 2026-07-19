package com.shikhi.app.data.auth

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
 */
class AuthRepositoryTest {

	private val dispatcher = StandardTestDispatcher()

	private class FakeTokenStore(access: String?, private var refresh: String?) : TokenStore {
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
	}

	private fun repository(
		authApi: AuthApi,
		userApi: UserApi,
		tokenStore: TokenStore,
		online: Boolean = true,
	): AuthRepository = AuthRepository(
		authApi = authApi,
		userApi = { userApi },
		tokenStore = tokenStore,
		connectivity = mockk<ConnectivityChecker>(relaxed = true) { every { isOnline() } returns online },
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
	fun `offline at bootstrap fast-fails to GuestUnavailable without calling guest()`() = runTest(dispatcher) {
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)

		val repo = repository(authApi, userApi, FakeTokenStore(null, null), online = false)
		repo.bootstrap()
		dispatcher.scheduler.advanceUntilIdle()

		assertEquals(SessionState.GuestUnavailable, repo.session.value)
		coVerify(exactly = 0) { authApi.guest(any()) }
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
}
