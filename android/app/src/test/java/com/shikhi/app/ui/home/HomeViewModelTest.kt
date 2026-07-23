package com.shikhi.app.ui.home

import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.content.CachedContentRepository
import com.shikhi.app.data.progress.LevelRepository
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 * UO3 (`~/.claude/plans/unified-offline-online/UO3.md`): [HomeViewModel.setLevel] must work
 * offline — it is routed entirely through [LevelRepository] (local projection + `"stats"` cache
 * write + a buffered `SET_LEVEL` outbox event) instead of a direct network call, so it must
 * never depend on any [com.shikhi.app.data.api.ProgressApi] call succeeding.
 */
class HomeViewModelTest {

	private val dispatcher = StandardTestDispatcher()

	private lateinit var authRepository: AuthRepository
	private lateinit var authApi: AuthApi
	private lateinit var content: CachedContentRepository
	private lateinit var levelRepository: LevelRepository

	@Before
	fun setUp() {
		Dispatchers.setMain(dispatcher)
		authRepository = mockk {
			every { session } returns MutableStateFlow(SessionState.Active(User(id = "user-1")))
		}
		authApi = mockk()
		// checkHealth() runs in init on every test — keep it out of the way with an immediate
		// success so it doesn't loop through the warming-retry delays.
		coEvery { authApi.health() } returns retrofit2.Response.success(Unit)
		content = mockk()
		levelRepository = mockk()
	}

	@After
	fun tearDown() = Dispatchers.resetMain()

	private fun viewModel() = HomeViewModel(authRepository, authApi, content, levelRepository)

	@Test
	fun `offline level change updates state optimistically without any network call`() = runTest(dispatcher) {
		// A Stats must already be in state for setLevel's optimistic `stats?.copy(cefrLevel=...)`
		// to have anything to flip — mirror the real flow where refresh() populates it first.
		coEvery { content.stats() } returns com.shikhi.app.data.content.Sourced(Stats(xp = 10, cefrLevel = "A1"), fromCache = false)
		coEvery { content.curriculum() } returns null
		coEvery { levelRepository.setLevel("B1") } returns Unit
		val vm = viewModel()
		vm.refresh()
		dispatcher.scheduler.advanceUntilIdle()
		assertEquals("A1", vm.state.value.stats?.cefrLevel)

		vm.setLevel("B1")
		dispatcher.scheduler.advanceUntilIdle()

		val state = vm.state.value
		assertEquals("B1", state.stats?.cefrLevel)
		assertFalse(state.savingLevel)
		assertFalse(state.levelError)
		coVerify(exactly = 1) { levelRepository.setLevel("B1") }
	}

	@Test
	fun `offline level change preserves the rest of the existing stats in state`() = runTest(dispatcher) {
		coEvery { content.stats() } returns null
		coEvery { content.curriculum() } returns null
		coEvery { levelRepository.setLevel("B1") } returns Unit
		val vm = viewModel()

		// Seed some stats into state via a successful refresh() first.
		coEvery { content.stats() } returns com.shikhi.app.data.content.Sourced(Stats(xp = 40, hearts = 4, cefrLevel = "A2"), fromCache = false)
		vm.refresh()
		dispatcher.scheduler.advanceUntilIdle()
		assertEquals(40, vm.state.value.stats?.xp)

		vm.setLevel("B1")
		dispatcher.scheduler.advanceUntilIdle()

		val state = vm.state.value
		assertEquals("level change must not clobber other stats fields", 40, state.stats?.xp)
		assertEquals(4, state.stats?.hearts)
		assertEquals("B1", state.stats?.cefrLevel)
	}

	@Test
	fun `a LevelRepository failure surfaces levelError instead of throwing`() = runTest(dispatcher) {
		coEvery { levelRepository.setLevel(any()) } throws RuntimeException("unexpected local failure")
		val vm = viewModel()

		vm.setLevel("B1")
		dispatcher.scheduler.advanceUntilIdle()

		val state = vm.state.value
		assertFalse(state.savingLevel)
		assertEquals(true, state.levelError)
	}

	@Test
	fun `a second setLevel call while one is already saving is ignored`() = runTest(dispatcher) {
		coEvery { levelRepository.setLevel(any()) } returns Unit
		val vm = viewModel()

		vm.setLevel("B1")
		vm.setLevel("C1") // should be dropped — savingLevel is already true
		dispatcher.scheduler.advanceUntilIdle()

		coVerify(exactly = 1) { levelRepository.setLevel(any()) }
		coVerify(exactly = 0) { levelRepository.setLevel("C1") }
	}
}
