package com.shikhi.app.ui.onboarding

import app.cash.turbine.test
import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.LoginPrefs
import com.shikhi.app.data.auth.RememberedLogin
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
 * Mirrors the web AuthPanel tests: a remembered login prefills a later visit with the box
 * already ticked, and an empty store leaves the form blank. "Remember me" now stores the
 * password too (sealed at rest by the real LoginPrefs).
 */
class OnboardingViewModelTest {

	private val dispatcher = StandardTestDispatcher()

	private class FakeLoginPrefs(initial: RememberedLogin? = null) : LoginPrefs {
		var stored: RememberedLogin? = initial
		override suspend fun remembered(): RememberedLogin? = stored
		override suspend fun setRemembered(email: String?, password: String?) {
			stored = if (email.isNullOrBlank()) null else RememberedLogin(email, password)
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
	fun `prefills remembered email and password and ticks the box`() = runTest(dispatcher) {
		val prefs = FakeLoginPrefs(RememberedLogin("nadia@example.com", "s3cretpassword"))
		val vm = OnboardingViewModel(repository(), prefs)

		vm.state.test {
			// The initial empty state, then the one the load coroutine fills in.
			assertEquals("", awaitItem().email)
			val loaded = awaitItem()
			assertEquals("nadia@example.com", loaded.email)
			assertEquals("s3cretpassword", loaded.password)
			assertTrue(loaded.rememberMe)
		}
	}

	@Test
	fun `an empty store leaves the form blank and unchecked`() = runTest(dispatcher) {
		val prefs = FakeLoginPrefs(initial = null)
		val vm = OnboardingViewModel(repository(), prefs)

		val s = vm.state.value
		assertEquals("", s.email)
		assertEquals("", s.password)
		assertFalse(s.rememberMe)
		assertNull(prefs.stored)
	}
}
