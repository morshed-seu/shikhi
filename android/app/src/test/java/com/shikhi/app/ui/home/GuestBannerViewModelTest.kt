package com.shikhi.app.ui.home

import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * GF5: covers GF3's sign-in-vs-claim mode split (the discard-confirmation gate in front of
 * login(), unaffected by the claim() path) and GF4's cold-start-vs-real-error distinction
 * (serverWarming). OG4 adds the LocalGuest disabled state.
 */
class GuestBannerViewModelTest {

	private val dispatcher = StandardTestDispatcher()

	/** OG4: the VM observes this on construction, so every test has to supply a real flow. */
	private val sessions = MutableStateFlow<SessionState>(SessionState.LoggedOut)

	@Before
	fun setUp() = Dispatchers.setMain(dispatcher)

	@After
	fun tearDown() = Dispatchers.resetMain()

	private fun user() = User(id = "u-1", isGuest = true)

	private fun authRepo(): AuthRepository = mockk<AuthRepository>(relaxed = true).also {
		every { it.session } returns sessions
	}

	private fun fillForm(vm: GuestBannerViewModel, email: String = "a@example.com", password: String = "password1") {
		vm.setEmail(email)
		vm.setPassword(password)
	}

	@Test
	fun `submit in SIGN_IN mode gates on confirmation instead of calling login`() = runTest(dispatcher) {
		val authRepository = authRepo()
		val vm = GuestBannerViewModel(authRepository)

		vm.selectSignIn()
		fillForm(vm)
		vm.submit()
		dispatcher.scheduler.advanceUntilIdle()

		assertTrue(vm.state.value.confirmingDiscard)
		coVerify(exactly = 0) { authRepository.login(any(), any()) }
	}

	@Test
	fun `confirmDiscardAndSignIn calls login exactly once and resets the confirmation flag`() = runTest(dispatcher) {
		val authRepository = authRepo()
		coEvery { authRepository.login(any(), any()) } returns Unit
		val vm = GuestBannerViewModel(authRepository)

		vm.selectSignIn()
		fillForm(vm, email = "b@example.com", password = "password2")
		vm.submit()
		dispatcher.scheduler.advanceUntilIdle()
		assertTrue(vm.state.value.confirmingDiscard)

		vm.confirmDiscardAndSignIn()
		dispatcher.scheduler.advanceUntilIdle()

		coVerify(exactly = 1) { authRepository.login("b@example.com", "password2") }
		assertFalse(vm.state.value.confirmingDiscard)
	}

	@Test
	fun `cancelDiscardConfirm resets the flag without ever calling login`() = runTest(dispatcher) {
		val authRepository = authRepo()
		val vm = GuestBannerViewModel(authRepository)

		vm.selectSignIn()
		fillForm(vm)
		vm.submit()
		dispatcher.scheduler.advanceUntilIdle()
		assertTrue(vm.state.value.confirmingDiscard)

		vm.cancelDiscardConfirm()
		dispatcher.scheduler.advanceUntilIdle()

		assertFalse(vm.state.value.confirmingDiscard)
		coVerify(exactly = 0) { authRepository.login(any(), any()) }
	}

	@Test
	fun `submit in default CLAIM mode calls claim directly, no confirmation gate`() = runTest(dispatcher) {
		val authRepository = authRepo()
		coEvery { authRepository.claim(any(), any(), any()) } returns Unit
		val vm = GuestBannerViewModel(authRepository)

		vm.open()
		fillForm(vm, email = "c@example.com", password = "password3")
		vm.submit()
		dispatcher.scheduler.advanceUntilIdle()

		coVerify(exactly = 1) { authRepository.claim("c@example.com", "password3", null) }
		assertFalse(vm.state.value.confirmingDiscard)
	}

	@Test
	fun `an IOException from claim sets serverWarming with no error message`() = runTest(dispatcher) {
		val authRepository = authRepo()
		coEvery { authRepository.claim(any(), any(), any()) } throws IOException()
		val vm = GuestBannerViewModel(authRepository)

		vm.open()
		fillForm(vm)
		vm.submit()
		dispatcher.scheduler.advanceUntilIdle()

		val s = vm.state.value
		assertTrue(s.serverWarming)
		assertNull(s.error)
	}

	@Test
	fun `an HttpException from claim does not set serverWarming`() = runTest(dispatcher) {
		val authRepository = authRepo()
		val conflict = HttpException(Response.error<Unit>(409, "".toResponseBody("application/json".toMediaType())))
		coEvery { authRepository.claim(any(), any(), any()) } throws conflict
		val vm = GuestBannerViewModel(authRepository)

		vm.open()
		fillForm(vm)
		vm.submit()
		dispatcher.scheduler.advanceUntilIdle()

		assertFalse(vm.state.value.serverWarming)
	}

	// --- OG4: no server account exists in LocalGuest, so claim/sign-in must be inert ---

	@Test
	fun `LocalGuest disables the banner actions and shows the pending note`() = runTest(dispatcher) {
		sessions.value = SessionState.LocalGuest
		val authRepository = authRepo()
		val vm = GuestBannerViewModel(authRepository)
		dispatcher.scheduler.advanceUntilIdle()

		assertTrue(vm.state.value.localGuest)

		// Both entry points are no-ops, so the form never opens.
		vm.open()
		vm.selectSignIn()
		dispatcher.scheduler.advanceUntilIdle()

		assertFalse(vm.state.value.open)
	}

	@Test
	fun `submit does nothing while LocalGuest`() = runTest(dispatcher) {
		sessions.value = SessionState.LocalGuest
		val authRepository = authRepo()
		val vm = GuestBannerViewModel(authRepository)
		dispatcher.scheduler.advanceUntilIdle()

		fillForm(vm)
		vm.submit()
		vm.confirmDiscardAndSignIn()
		dispatcher.scheduler.advanceUntilIdle()

		assertFalse(vm.state.value.confirmingDiscard)
		assertFalse(vm.state.value.submitting)
		coVerify(exactly = 0) { authRepository.claim(any(), any(), any()) }
		coVerify(exactly = 0) { authRepository.login(any(), any()) }
	}

	@Test
	fun `registration completing re-enables the actions`() = runTest(dispatcher) {
		sessions.value = SessionState.LocalGuest
		val authRepository = authRepo()
		coEvery { authRepository.claim(any(), any(), any()) } returns Unit
		val vm = GuestBannerViewModel(authRepository)
		dispatcher.scheduler.advanceUntilIdle()
		assertTrue(vm.state.value.localGuest)

		// GuestRegistrationWorker finished: LocalGuest -> Active.
		sessions.value = SessionState.Active(user())
		dispatcher.scheduler.advanceUntilIdle()

		assertFalse(vm.state.value.localGuest)

		vm.open()
		fillForm(vm, email = "d@example.com", password = "password4")
		vm.submit()
		dispatcher.scheduler.advanceUntilIdle()

		assertTrue(vm.state.value.open)
		coVerify(exactly = 1) { authRepository.claim("d@example.com", "password4", null) }
	}

	@Test
	fun `an open form collapses if the session drops back to LocalGuest`() = runTest(dispatcher) {
		val authRepository = authRepo()
		val vm = GuestBannerViewModel(authRepository)
		dispatcher.scheduler.advanceUntilIdle()

		vm.selectSignIn()
		fillForm(vm)
		vm.submit()
		dispatcher.scheduler.advanceUntilIdle()
		assertTrue(vm.state.value.confirmingDiscard)

		sessions.value = SessionState.LocalGuest
		dispatcher.scheduler.advanceUntilIdle()

		assertFalse(vm.state.value.open)
		assertFalse(vm.state.value.confirmingDiscard)
	}
}
