package com.shikhi.app.ui.practice

import app.cash.turbine.test
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.PracticeExercise
import com.shikhi.app.data.api.dto.PracticeExerciseConfig
import com.shikhi.app.data.api.dto.PracticeResult
import com.shikhi.app.data.api.dto.PracticeRound
import com.shikhi.app.data.api.dto.Verdict
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.connectivity.ConnectivityChecker
import com.shikhi.app.data.practice.LocalPracticeSource
import com.shikhi.app.data.practice.PracticeGradeOutcome
import com.shikhi.app.data.practice.RemotePracticeSource
import com.shikhi.app.ui.util.Pronouncer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Confirms the connectivity-based [com.shikhi.app.data.practice.PracticePlaySource] resolution
 * (OF4, docs/93-offline-learning-design.md §3.3), mirroring `LessonViewModelTest`'s coverage of
 * the same pattern for lessons: the source is picked once at session start, `check()`/`finish()`
 * delegate to whichever one is active, and `onCleared()` still finalizes an unfinished session.
 */
class PracticeViewModelTest {

	private val dispatcher = StandardTestDispatcher()

	private val round = PracticeRound(
		sessionId = "session-1",
		round = 1,
		cefrLevel = "A1",
		levelUpEligible = false,
		exercises = listOf(
			PracticeExercise(
				id = "ex-1",
				type = "WORD_MEANING",
				prompt = Bilingual("What does “apple” mean?", "“apple” শব্দের অর্থ কী?"),
				config = PracticeExerciseConfig(options = emptyList()),
			),
		),
	)

	private lateinit var remoteSource: RemotePracticeSource
	private lateinit var localSource: LocalPracticeSource
	private lateinit var connectivity: ConnectivityChecker
	private lateinit var authRepository: AuthRepository
	private lateinit var progressApi: ProgressApi
	private lateinit var pronouncer: Pronouncer

	@Before
	fun setUp() {
		Dispatchers.setMain(dispatcher)
		remoteSource = mockk()
		localSource = mockk()
		connectivity = mockk()
		authRepository = mockk {
			// Default: a registered session, so the connectivity-only tests below are unaffected —
			// LocalGuest routing is covered by its own dedicated test.
			every { session } returns MutableStateFlow(SessionState.Active(mockk(relaxed = true)))
		}
		progressApi = mockk(relaxed = true)
		// The hearts-refresh stats() call is a fire-and-forget side effect (runCatching { ... }
		// .onSuccess { ... }) most of these tests aren't exercising — making it fail keeps state
		// emissions to exactly Loading + Playing so turbine's `.test { }` blocks don't need to
		// account for a third "hearts updated" emission they don't care about.
		coEvery { progressApi.stats() } throws RuntimeException("stats not needed in these tests")
		pronouncer = mockk()
		every { pronouncer.isAvailable } returns MutableStateFlow(true)
		every { pronouncer.speak(any()) } just runs
	}

	@After
	fun tearDown() = Dispatchers.resetMain()

	private fun viewModel(appScope: kotlinx.coroutines.CoroutineScope = TestScope(dispatcher)) =
		PracticeViewModel(remoteSource, localSource, connectivity, authRepository, progressApi, pronouncer, appScope)

	@Test
	fun `online device starts the remote source and never touches the local source`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns true
		coEvery { remoteSource.start() } returns round

		val vm = viewModel()

		vm.state.test {
			assertEquals(PracticeUiState.Loading, awaitItem())
			val playing = awaitItem() as PracticeUiState.Playing
			assertEquals("session-1", playing.round.sessionId)
		}
		coVerify(exactly = 0) { localSource.start() }
	}

	@Test
	fun `offline device starts the local source and never touches the remote source`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns false
		coEvery { localSource.start() } returns round

		val vm = viewModel()

		vm.state.test {
			assertEquals(PracticeUiState.Loading, awaitItem())
			val playing = awaitItem() as PracticeUiState.Playing
			assertEquals("session-1", playing.round.sessionId)
		}
		coVerify(exactly = 0) { remoteSource.start() }
	}

	@Test
	fun `an online but unregistered LocalGuest still starts the local source, never the remote one`() = runTest(dispatcher) {
		// OG-fix regression: before this fix, source selection was based solely on
		// ConnectivityChecker.isOnline() — a LocalGuest that gets connectivity before
		// GuestRegistrationWorker finishes would route to RemotePracticeSource, which has no
		// access token yet and would 401 ("Could not start the session").
		every { connectivity.isOnline() } returns true
		every { authRepository.session } returns MutableStateFlow(SessionState.LocalGuest)
		coEvery { localSource.start() } returns round

		val vm = viewModel()

		vm.state.test {
			assertEquals(PracticeUiState.Loading, awaitItem())
			val playing = awaitItem() as PracticeUiState.Playing
			assertEquals("session-1", playing.round.sessionId)
		}
		coVerify(exactly = 0) { remoteSource.start() }
	}

	@Test
	fun `offline start skips the network stats call for hearts`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns false
		coEvery { localSource.start() } returns round

		val vm = viewModel()
		vm.state.test { awaitItem(); awaitItem() }
		dispatcher.scheduler.advanceUntilIdle()

		coVerify(exactly = 0) { progressApi.stats() }
	}

	@Test
	fun `check delegates grading to whichever source is active and preserves hearts when the outcome has none`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns false
		coEvery { localSource.start() } returns round
		coEvery { localSource.grade("session-1", any(), any()) } returns
			PracticeGradeOutcome(Verdict(correct = true, feedback = Bilingual("Correct!", "সঠিক!")), hearts = null)

		val vm = viewModel()
		vm.state.test { awaitItem(); awaitItem() } // drain Loading + Playing

		vm.selectOption("opt-1")
		vm.check()
		dispatcher.scheduler.advanceUntilIdle()

		val state = vm.state.value as PracticeUiState.Playing
		assertTrue(state.verdict!!.correct)
		assertEquals(listOf(true), state.strokes)
	}

	@Test
	fun `finish delegates completion to the active source`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns true
		coEvery { remoteSource.start() } returns round
		coEvery { remoteSource.complete("session-1") } returns PracticeResult(correctCount = 3, totalCount = 10, xpEarned = 30)

		val vm = viewModel()
		vm.state.test { awaitItem(); awaitItem() }

		vm.finish()
		dispatcher.scheduler.advanceUntilIdle()

		val finished = vm.state.value as PracticeUiState.Finished
		assertEquals(3, finished.result.correctCount)
	}

	@Test
	fun `leaving mid-session still finalizes it via the active source in the app scope`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns false
		coEvery { localSource.start() } returns round
		coEvery { localSource.complete("session-1") } returns PracticeResult(correctCount = 0, totalCount = 0)

		val appScope = TestScope(dispatcher)
		val vm = viewModel(appScope)
		vm.state.test { awaitItem(); awaitItem() }

		vm.onClearedForTest()
		dispatcher.scheduler.advanceUntilIdle()

		coVerify { localSource.complete("session-1") }
	}

	@Test
	fun `pronounce delegates to the injected Pronouncer`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns true
		coEvery { remoteSource.start() } returns round
		val vm = viewModel()
		vm.state.test { awaitItem(); awaitItem() }

		vm.pronounce("apple")

		io.mockk.verify { pronouncer.speak("apple") }
	}

	@Test
	fun `speechAvailable mirrors the Pronouncer's availability flow`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns true
		coEvery { remoteSource.start() } returns round
		val vm = viewModel()

		assertEquals(true, vm.speechAvailable.value)
	}
}

/** Exposes [PracticeViewModel.onCleared] for the "leaving mid-session" test above without making
 * the real lifecycle method itself `public` beyond what `ViewModel` already requires. */
private fun PracticeViewModel.onClearedForTest() {
	val method = PracticeViewModel::class.java.getDeclaredMethod("onCleared")
	method.isAccessible = true
	method.invoke(this)
}
