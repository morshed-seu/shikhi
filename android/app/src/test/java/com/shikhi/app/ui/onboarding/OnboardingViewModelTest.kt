package com.shikhi.app.ui.onboarding

import app.cash.turbine.test
import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.LoginPrefs
import com.shikhi.app.data.auth.TokenStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Mirrors the web AuthPanel tests: a remembered login email prefills a later visit with the
 * box already ticked, and an empty store leaves the form blank. The password is never
 * handed to LoginPrefs (only the email is).
 */
class OnboardingViewModelTest {

	private val dispatcher = StandardTestDispatcher()

	private class FakeLoginPrefs(initial: String? = null) : LoginPrefs {
		var stored: String? = initial
		override suspend fun rememberedEmail(): String? = stored
		override suspend fun setRememberedEmail(email: String?) {
			stored = email
		}
	}

	private fun repository(): AuthRepository {
		val tokenStore = mockk<TokenStore>()
		every { tokenStore.accessToken } returns MutableStateFlow<String?>(null)
		return AuthRepository(
			authApi = mockk<AuthApi>(relaxed = true),
			userApi = { mockk<UserApi>(relaxed = true) },
			tokenStore = tokenStore,
			appScope = CoroutineScope(dispatcher),
		)
	}

	@Before
	fun setUp() = Dispatchers.setMain(dispatcher)

	@After
	fun tearDown() = Dispatchers.resetMain()

	@Test
	fun `prefills remembered email and ticks the box`() = runTest(dispatcher) {
		val prefs = FakeLoginPrefs(initial = "nadia@example.com")
		val vm = OnboardingViewModel(repository(), prefs)

		vm.state.test {
			// The initial empty state, then the one the load coroutine fills in.
			assertEquals("", awaitItem().email)
			val loaded = awaitItem()
			assertEquals("nadia@example.com", loaded.email)
			assertTrue(loaded.rememberMe)
		}
	}

	@Test
	fun `an empty store leaves the form blank and unchecked`() = runTest(dispatcher) {
		val prefs = FakeLoginPrefs(initial = null)
		val vm = OnboardingViewModel(repository(), prefs)

		val s = vm.state.value
		assertEquals("", s.email)
		assertFalse(s.rememberMe)
		assertNull(prefs.stored)
	}
}
