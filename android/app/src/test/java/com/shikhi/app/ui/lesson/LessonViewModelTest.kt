package com.shikhi.app.ui.lesson

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.Exercise
import com.shikhi.app.data.api.dto.LessonResult
import com.shikhi.app.data.api.dto.LessonView
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.Verdict
import com.shikhi.app.data.connectivity.ConnectivityChecker
import com.shikhi.app.data.lesson.GradeOutcome
import com.shikhi.app.data.lesson.LocalLessonSource
import com.shikhi.app.data.lesson.PlayableLesson
import com.shikhi.app.data.lesson.RemoteLessonSource
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Confirms the connectivity-based [com.shikhi.app.data.lesson.LessonPlaySource] resolution
 * (OF3, docs/93-offline-learning-design.md §3.3): it's picked once at session start from
 * [ConnectivityChecker], and the pre-existing remote-completion-failure outbox fallback keeps
 * working unchanged now that it's routed through the source abstraction.
 */
class LessonViewModelTest {

	private val dispatcher = StandardTestDispatcher()

	private val lessonView = LessonView(
		id = "lesson-1",
		title = Bilingual("Greetings", "শুভেচ্ছা"),
		exercises = listOf(
			Exercise(id = "ex-1", type = "MCQ", prompt = Bilingual("Pick hello", "হ্যালো বাছুন")),
		),
	)

	private lateinit var remoteSource: RemoteLessonSource
	private lateinit var localSource: LocalLessonSource
	private lateinit var connectivity: ConnectivityChecker
	private lateinit var outbox: OutboxRepository

	@Before
	fun setUp() {
		Dispatchers.setMain(dispatcher)
		remoteSource = mockk()
		localSource = mockk()
		connectivity = mockk()
		outbox = mockk(relaxed = true)
	}

	@After
	fun tearDown() = Dispatchers.resetMain()

	private fun savedState() = SavedStateHandle(mapOf("lessonId" to "lesson-1"))

	@Test
	fun `online device starts the remote source and never touches the local source`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns true
		coEvery { remoteSource.start("lesson-1") } returns PlayableLesson("session-1", lessonView, heartsRemaining = 5)

		val vm = LessonViewModel(savedState(), remoteSource, localSource, connectivity, outbox)

		vm.state.test {
			assertEquals(LessonUiState.Loading, awaitItem())
			val playing = awaitItem() as LessonUiState.Playing
			assertEquals("lesson-1", playing.lesson.id)
			assertEquals(5, playing.hearts)
		}
		coVerify(exactly = 0) { localSource.start(any()) }
	}

	@Test
	fun `offline device starts the local source and never touches the remote source`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns false
		coEvery { localSource.start("lesson-1") } returns PlayableLesson("session-1", lessonView, heartsRemaining = 5)

		val vm = LessonViewModel(savedState(), remoteSource, localSource, connectivity, outbox)

		vm.state.test {
			assertEquals(LessonUiState.Loading, awaitItem())
			val playing = awaitItem() as LessonUiState.Playing
			assertEquals("lesson-1", playing.lesson.id)
		}
		coVerify(exactly = 0) { remoteSource.start(any()) }
	}

	@Test
	fun `check delegates grading to whichever source is active`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns false
		coEvery { localSource.start("lesson-1") } returns PlayableLesson("session-1", lessonView, heartsRemaining = 5)
		coEvery { localSource.grade("session-1", any(), any()) } returns
			GradeOutcome(Verdict(correct = true, feedback = Bilingual("Correct!", "সঠিক!")), heartsRemaining = 5)

		val vm = LessonViewModel(savedState(), remoteSource, localSource, connectivity, outbox)
		vm.state.test { awaitItem(); awaitItem() } // drain Loading + Playing

		vm.selectOption("opt-correct")
		vm.check()
		dispatcher.scheduler.advanceUntilIdle()

		val state = vm.state.value as LessonUiState.Playing
		assertTrue(state.verdict!!.correct)
		assertEquals(1, state.correctCount)
	}

	@Test
	fun `completing an offline-sourced lesson always reports savedOffline and skips the flush`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns false
		coEvery { localSource.start("lesson-1") } returns PlayableLesson("session-1", lessonView, heartsRemaining = 5)
		coEvery { localSource.complete("session-1", any()) } returns
			LessonResult(score = 0, xpEarned = 0, stats = Stats(hearts = 5))

		val vm = LessonViewModel(savedState(), remoteSource, localSource, connectivity, outbox)
		vm.state.test { awaitItem(); awaitItem() }

		vm.skipUnsupported() // the only exercise, so this completes the lesson
		dispatcher.scheduler.advanceUntilIdle()

		val finished = vm.state.value as LessonUiState.Finished
		assertTrue("a local-source completion is never server-confirmed", finished.savedOffline)
		coVerify(exactly = 0) { outbox.flush() }
	}

	@Test
	fun `a remote completion failure still buffers the pre-existing COMPLETE_LESSON outbox event`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns true
		coEvery { remoteSource.start("lesson-1") } returns PlayableLesson("session-1", lessonView, heartsRemaining = 5)
		coEvery { remoteSource.complete("session-1", any()) } throws IOException("offline mid-session")

		val vm = LessonViewModel(savedState(), remoteSource, localSource, connectivity, outbox)
		vm.state.test { awaitItem(); awaitItem() }

		vm.skipUnsupported()
		dispatcher.scheduler.advanceUntilIdle()

		val finished = vm.state.value as LessonUiState.Finished
		assertTrue(finished.savedOffline)
		coVerify {
			outbox.enqueue(
				OutboxEventType.COMPLETE_LESSON,
				buildJsonObject {
					put("lessonId", "lesson-1")
					put("score", 0)
				},
			)
		}
	}

	@Test
	fun `a remote completion failing with a non-recoverable 4xx surfaces LoadError, not saved-offline`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns true
		coEvery { remoteSource.start("lesson-1") } returns PlayableLesson("session-1", lessonView, heartsRemaining = 5)
		val notFound = HttpException(Response.error<Unit>(404, "".toResponseBody("application/json".toMediaType())))
		coEvery { remoteSource.complete("session-1", any()) } throws notFound

		val vm = LessonViewModel(savedState(), remoteSource, localSource, connectivity, outbox)
		vm.state.test { awaitItem(); awaitItem() }

		vm.skipUnsupported()
		dispatcher.scheduler.advanceUntilIdle()

		assertEquals(LessonUiState.LoadError, vm.state.value)
	}

	@Test
	fun `a successful remote completion is not saved-offline and triggers the post-lesson flush`() = runTest(dispatcher) {
		every { connectivity.isOnline() } returns true
		coEvery { remoteSource.start("lesson-1") } returns PlayableLesson("session-1", lessonView, heartsRemaining = 5)
		coEvery { remoteSource.complete("session-1", any()) } returns LessonResult(score = 0, xpEarned = 10, stats = Stats(hearts = 5))

		val vm = LessonViewModel(savedState(), remoteSource, localSource, connectivity, outbox)
		vm.state.test { awaitItem(); awaitItem() }

		vm.skipUnsupported()
		dispatcher.scheduler.advanceUntilIdle()

		val finished = vm.state.value as LessonUiState.Finished
		assertEquals(false, finished.savedOffline)
		coVerify { outbox.flush() }
	}
}
