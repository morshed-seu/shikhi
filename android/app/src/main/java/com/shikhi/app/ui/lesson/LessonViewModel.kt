package com.shikhi.app.ui.lesson

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.api.dto.LessonResult
import com.shikhi.app.data.api.dto.LessonView
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.Verdict
import com.shikhi.app.data.connectivity.ConnectivityChecker
import com.shikhi.app.data.lesson.LessonPlaySource
import com.shikhi.app.data.lesson.LocalLessonSource
import com.shikhi.app.data.lesson.RemoteLessonSource
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * Lesson play-through state machine, a direct port of the web player
 * (frontend/src/components/LessonPlayer.tsx): load lesson + start session → per-exercise
 * answer with grading → complete. Grading/completion are backed by a [LessonPlaySource]
 * resolved once per session start (OF3, docs/93-offline-learning-design.md §3.3): online users
 * get [RemoteLessonSource] (today's server-graded behavior, unchanged); offline devices get
 * [LocalLessonSource] (bundled content + the local grading engine). A remote completion that
 * fails for a network/5xx reason is still buffered to the outbox and shown as "saved offline"
 * (D2/NFR-N2), exactly as before OF3.
 */
sealed interface LessonUiState {
	data object Loading : LessonUiState

	data object LoadError : LessonUiState

	data class Playing(
		val lesson: LessonView,
		val index: Int,
		val hearts: Int,
		val selectedOptionId: String? = null,
		val placedTokenIds: List<String> = emptyList(),
		val textAnswer: String = "",
		val verdict: Verdict? = null,
		val busy: Boolean = false,
		val correctCount: Int = 0,
	) : LessonUiState {
		val exercise get() = lesson.exercises[index]
		val isLast get() = index == lesson.exercises.lastIndex
	}

	data class Finished(
		val result: LessonResult,
		val total: Int,
		val savedOffline: Boolean,
	) : LessonUiState
}

/** Exercise types answered with free text (web player TEXT_TYPES). */
val TEXT_TYPES = setOf("TYPE_TRANSLATION", "FILL_BLANK")

@HiltViewModel
class LessonViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	private val remoteSource: RemoteLessonSource,
	private val localSource: LocalLessonSource,
	private val connectivity: ConnectivityChecker,
	private val outbox: OutboxRepository,
) : ViewModel() {

	private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

	private val _state = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
	val state: StateFlow<LessonUiState> = _state

	private var sessionId: String? = null

	/** Resolved once in [init] and reused for the whole session — never re-checked mid-play
	 * (§3.3), so a session's grading source can't switch mid-answer. */
	private lateinit var source: LessonPlaySource

	/** True when [source] is [localSource] — a completion under this source is never confirmed
	 * by a live server round trip, so it always renders as "saved offline" (unlike the remote
	 * path, where that's only true on a completion failure). */
	private var usingLocalSource: Boolean = false

	init {
		viewModelScope.launch {
			try {
				usingLocalSource = !connectivity.isOnline()
				source = if (usingLocalSource) localSource else remoteSource
				val playable = source.start(lessonId)
				sessionId = playable.sessionId
				_state.value = LessonUiState.Playing(playable.lesson, index = 0, hearts = playable.heartsRemaining)
			} catch (e: Exception) {
				_state.value = LessonUiState.LoadError
			}
		}
	}

	fun selectOption(optionId: String) {
		_state.update { s ->
			if (s is LessonUiState.Playing && s.verdict == null) s.copy(selectedOptionId = optionId) else s
		}
	}

	fun placeToken(tokenId: String) {
		_state.update { s ->
			if (s is LessonUiState.Playing && s.verdict == null && tokenId !in s.placedTokenIds) {
				s.copy(placedTokenIds = s.placedTokenIds + tokenId)
			} else {
				s
			}
		}
	}

	fun setTextAnswer(value: String) {
		_state.update { s ->
			if (s is LessonUiState.Playing && s.verdict == null) s.copy(textAnswer = value) else s
		}
	}

	fun removeToken(tokenId: String) {
		_state.update { s ->
			if (s is LessonUiState.Playing && s.verdict == null) {
				s.copy(placedTokenIds = s.placedTokenIds - tokenId)
			} else {
				s
			}
		}
	}

	fun check() {
		val s = _state.value as? LessonUiState.Playing ?: return
		val session = sessionId ?: return
		if (s.busy || s.verdict != null) return
		val answer = when (s.exercise.type) {
			"MCQ" -> {
				val selected = s.selectedOptionId ?: return
				buildJsonObject { put("selectedOptionId", selected) }
			}

			"WORD_BANK" -> {
				val tokens = s.exercise.config.tokens.orEmpty()
				if (s.placedTokenIds.size != tokens.size || tokens.isEmpty()) return
				// Grading joins the arranged words: send token text in placed order (web parity).
				val byId = tokens.associate { it.id to it.text.en }
				buildJsonObject {
					putJsonArray("tokenOrder") { s.placedTokenIds.forEach { add(byId[it] ?: "") } }
				}
			}

			in TEXT_TYPES -> {
				if (s.textAnswer.isBlank()) return
				buildJsonObject { put("text", s.textAnswer) }
			}

			else -> return
		}
		_state.value = s.copy(busy = true)
		viewModelScope.launch {
			try {
				val outcome = source.grade(session, s.exercise, answer)
				_state.update { cur ->
					(cur as? LessonUiState.Playing)?.copy(
						busy = false,
						verdict = outcome.verdict,
						hearts = outcome.heartsRemaining,
						correctCount = cur.correctCount + if (outcome.verdict.correct) 1 else 0,
					) ?: cur
				}
			} catch (e: Exception) {
				// Web parity: a failed submit shows as "not quite" without consuming state.
				_state.update { cur ->
					(cur as? LessonUiState.Playing)?.copy(busy = false, verdict = Verdict(correct = false)) ?: cur
				}
			}
		}
	}

	/** Advance past an exercise the app can't render (PRD 21 §2 fallback card). */
	fun skipUnsupported() = advance()

	fun next() = advance()

	private fun advance() {
		val s = _state.value as? LessonUiState.Playing ?: return
		if (s.busy) return
		if (!s.isLast) {
			_state.value = s.copy(
				index = s.index + 1,
				selectedOptionId = null,
				placedTokenIds = emptyList(),
				textAnswer = "",
				verdict = null,
			)
			return
		}
		complete(s)
	}

	private fun complete(s: LessonUiState.Playing) {
		val session = sessionId ?: return
		_state.value = s.copy(busy = true)
		viewModelScope.launch {
			try {
				val result = source.complete(session, s.correctCount)
				_state.value = LessonUiState.Finished(result, total = s.lesson.exercises.size, savedOffline = usingLocalSource)
				if (!usingLocalSource) {
					// Post-lesson flush trigger: reconcile anything buffered from earlier failures.
					// (LocalLessonSource already buffered this completion itself — no live
					// connection to flush against, so this call is remote-path-only.)
					outbox.flush()
				}
			} catch (e: Exception) {
				val recoverable = e is IOException || (e is HttpException && e.code() >= 500)
				if (recoverable) {
					finishOffline(s)
				} else {
					_state.value = LessonUiState.LoadError
				}
			}
		}
	}

	/** Buffer the completion for idempotent replay and show local results (web parity). */
	private suspend fun finishOffline(s: LessonUiState.Playing) {
		outbox.enqueue(
			OutboxEventType.COMPLETE_LESSON,
			buildJsonObject {
				put("lessonId", lessonId)
				put("score", s.correctCount)
			},
		)
		_state.value = LessonUiState.Finished(
			result = LessonResult(score = s.correctCount, xpEarned = 0, stats = Stats(hearts = s.hearts)),
			total = s.lesson.exercises.size,
			savedOffline = true,
		)
	}
}
