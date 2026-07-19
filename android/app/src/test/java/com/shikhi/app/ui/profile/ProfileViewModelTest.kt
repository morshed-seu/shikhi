package com.shikhi.app.ui.profile

import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.api.dto.DashboardResponse
import com.shikhi.app.data.api.dto.Identity
import com.shikhi.app.data.api.dto.RefreshRequest
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.TokenPair
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.api.dto.WordMasteryEntry
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.connectivity.ConnectivityChecker
import com.shikhi.app.data.content.Sourced
import com.shikhi.app.data.dashboard.DashboardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Gate MD3 unit tests: the dashboard snapshot maps into UI state, a linked-identities failure
 * never blanks the rest of the profile (same decision as the web client,
 * frontend/src/components/ProfileView.tsx), delete calls through to the same logout path the
 * home header used to own, and guests are flagged so the screen can swap in the claim CTA.
 */
class ProfileViewModelTest {

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

	private val dashboard = DashboardResponse(
		stats = Stats(hearts = 4, xp = 120, currentStreak = 3, longestStreak = 9, dailyGoal = 20, cefrLevel = "A2"),
		wordMastery = listOf(WordMasteryEntry("A1", mastered = 40, total = 100)),
		reviewDueCount = 5,
		lessonsCompleted = 2,
		practiceSessionsCompleted = 7,
		totalAnswered = 50,
		totalCorrect = 40,
	)

	/** Boots a real AuthRepository (mirrors OnboardingViewModelTest) to an Active session for [user]. */
	private fun activeAuthRepository(user: User, tokenStore: FakeTokenStore = FakeTokenStore(null, "rt")): AuthRepository {
		val authApi = mockk<AuthApi>(relaxed = true)
		val userApi = mockk<UserApi>(relaxed = true)
		coEvery { authApi.refresh(RefreshRequest("rt")) } returns TokenPair("at", "rt2", 3600)
		coEvery { userApi.me() } returns user
		val repo = AuthRepository(
			authApi = authApi,
			userApi = { userApi },
			tokenStore = tokenStore,
			connectivity = mockk<ConnectivityChecker>(relaxed = true) { every { isOnline() } returns true },
			appScope = CoroutineScope(dispatcher),
		)
		runBlocking { repo.bootstrap() }
		return repo
	}

	@Before
	fun setUp() = Dispatchers.setMain(dispatcher)

	@After
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun `dashboard, identities, and cefr level land in state`() = runTest(dispatcher) {
		val user = User(id = "u1", displayName = "Nadia", uiLocale = "bn", isGuest = false)
		val dashboardRepository = mockk<DashboardRepository>(relaxed = true)
		coEvery { dashboardRepository.dashboard() } returns Sourced(dashboard, fromCache = false)
		coEvery { dashboardRepository.identities() } returns listOf(Identity("EMAIL", true, "n***@example.com"))

		val vm = ProfileViewModel(activeAuthRepository(user), dashboardRepository)
		dispatcher.scheduler.advanceUntilIdle()

		val s = vm.state.value
		assertFalse(s.loading)
		assertFalse(s.error)
		assertFalse(s.fromCache)
		assertEquals(dashboard, s.dashboard)
		assertEquals(1, s.identities.size)
		assertEquals("n***@example.com", s.identities.single().maskedRef)
		assertEquals("Nadia", s.user?.displayName)
		assertFalse(s.isGuest)
	}

	@Test
	fun `an identities failure still lets the dashboard load`() = runTest(dispatcher) {
		val user = User(id = "u1", displayName = "Nadia", uiLocale = "bn", isGuest = false)
		val dashboardRepository = mockk<DashboardRepository>(relaxed = true)
		coEvery { dashboardRepository.dashboard() } returns Sourced(dashboard, fromCache = false)
		coEvery { dashboardRepository.identities() } throws RuntimeException("network down")

		val vm = ProfileViewModel(activeAuthRepository(user), dashboardRepository)
		dispatcher.scheduler.advanceUntilIdle()

		val s = vm.state.value
		assertFalse(s.loading)
		assertFalse("identities failing must not hard-error the profile", s.error)
		assertEquals(dashboard, s.dashboard)
		assertTrue(s.identities.isEmpty())
	}

	@Test
	fun `guest flag is exposed from the session`() = runTest(dispatcher) {
		val guest = User(id = "u1", displayName = null, uiLocale = "bn", isGuest = true)
		val dashboardRepository = mockk<DashboardRepository>(relaxed = true)
		coEvery { dashboardRepository.dashboard() } returns Sourced(dashboard, fromCache = false)
		coEvery { dashboardRepository.identities() } returns emptyList()

		val vm = ProfileViewModel(activeAuthRepository(guest), dashboardRepository)
		dispatcher.scheduler.advanceUntilIdle()

		assertTrue(vm.state.value.isGuest)
	}

	@Test
	fun `delete calls through and logs out, clearing the session`() = runTest(dispatcher) {
		val user = User(id = "u1", displayName = "Nadia", uiLocale = "bn", isGuest = false)
		val dashboardRepository = mockk<DashboardRepository>(relaxed = true)
		coEvery { dashboardRepository.dashboard() } returns Sourced(dashboard, fromCache = false)
		coEvery { dashboardRepository.identities() } returns emptyList()
		coEvery { dashboardRepository.deleteAccount() } returns Unit

		val tokenStore = FakeTokenStore(null, "rt")
		val authRepository = activeAuthRepository(user, tokenStore)
		val vm = ProfileViewModel(authRepository, dashboardRepository)
		dispatcher.scheduler.advanceUntilIdle()
		assertTrue(authRepository.session.value is SessionState.Active)

		vm.startDeleteConfirm()
		vm.deleteAccount()
		dispatcher.scheduler.advanceUntilIdle()

		coVerify(exactly = 1) { dashboardRepository.deleteAccount() }
		assertTrue("delete must log out (same path the home header used)", authRepository.session.value is SessionState.LoggedOut)
		assertTrue(tokenStore.cleared)
		assertFalse(vm.state.value.deleting)
	}

	@Test
	fun `a saved name propagates to the session so a fresh ProfileViewModel seeds with it`() = runTest(dispatcher) {
		// US-13.2 "shows everywhere": ProfileViewModel is back-stack-entry-scoped, so
		// Profile -> Home -> Profile builds a NEW ViewModel that seeds from the session.
		// If saveName only updated local UI state, the old name would reappear here.
		val user = User(id = "u1", displayName = "Old Name", uiLocale = "bn", isGuest = false)
		val updated = user.copy(displayName = "New Name")
		val dashboardRepository = mockk<DashboardRepository>(relaxed = true)
		coEvery { dashboardRepository.dashboard() } returns Sourced(dashboard, fromCache = false)
		coEvery { dashboardRepository.identities() } returns emptyList()
		coEvery { dashboardRepository.updateProfile(displayName = "New Name", uiLocale = null) } returns updated

		val authRepository = activeAuthRepository(user)
		val vm = ProfileViewModel(authRepository, dashboardRepository)
		dispatcher.scheduler.advanceUntilIdle()

		vm.startEditName()
		vm.setNameDraft("New Name")
		vm.saveName()
		dispatcher.scheduler.advanceUntilIdle()

		assertEquals("New Name", (authRepository.session.value as SessionState.Active).user.displayName)
		assertEquals("New Name", vm.state.value.user?.displayName)
		assertFalse(vm.state.value.editingName)

		val secondVisit = ProfileViewModel(authRepository, dashboardRepository)
		dispatcher.scheduler.advanceUntilIdle()
		assertEquals("New Name", secondVisit.state.value.user?.displayName)
	}

	@Test
	fun `a network-only dashboard failure with no cache surfaces the error state`() = runTest(dispatcher) {
		val user = User(id = "u1", displayName = "Nadia", uiLocale = "bn", isGuest = false)
		val dashboardRepository = mockk<DashboardRepository>(relaxed = true)
		coEvery { dashboardRepository.dashboard() } returns null
		coEvery { dashboardRepository.identities() } returns emptyList()

		val vm = ProfileViewModel(activeAuthRepository(user), dashboardRepository)
		dispatcher.scheduler.advanceUntilIdle()

		val s = vm.state.value
		assertFalse(s.loading)
		assertTrue(s.error)
		assertNull(s.dashboard)
	}
}
