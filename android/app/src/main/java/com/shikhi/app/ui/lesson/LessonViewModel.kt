package com.shikhi.app.ui.lesson

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.api.ContentApi
import com.shikhi.app.data.api.LearningApi
import com.shikhi.app.data.api.dto.IdempotentRequest
import com.shikhi.app.data.api.dto.LessonResult
import com.shikhi.app.data.api.dto.LessonView
import com.shikhi.app.data.api.dto.Stats
import com.shikhi.app.data.api.dto.SubmitAnswerRequest
import com.shikhi.app.data.api.dto.Verdict
import com.shikhi.app.data.outbox.OutboxEventType
import com.shikhi.app.data.outbox.OutboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import java.util.UUID
import javax.inject.Inject

/**
 * Lesson play-through state machine, a direct port of the web player
 * (frontend/src/components/LessonPlayer.tsx): load lesson + start session → per-exercise
 * answer with server-side grading → complete. Completion that fails for a network/5xx
 * reason is buffered to the outbox and shown as "saved offline" (D2/NFR-N2).
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
	private val contentApi: ContentApi,
	private val learningApi: LearningApi,
	private val outbox: OutboxRepository,
) : ViewModel() {

	private val lessonId: String = checkNotNull(savedStateHandle["lessonId"])

	private val _state = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
	val state: StateFlow<LessonUiState> = _state

	private var sessionId: String? = null

	init {
		viewModelScope.launch {
			try {
				coroutineScope {
					val lesson = async { contentApi.lesson(lessonId) }
					val session = async { learningApi.startSession(com.shikhi.app.data.api.dto.StartSessionRequest(lessonId)) }
					val started = session.await()
					sessionId = started.id
					_state.value = LessonUiState.Playing(lesson.await(), index = 0, hearts = started.heartsRemaining)
				}
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
				val res = learningApi.submitAnswer(
					session,
					SubmitAnswerRequest(
						idempotencyKey = UUID.randomUUID().toString(),
						exerciseId = s.exercise.id,
						answer = answer,
					),
				)
				_state.update { cur ->
					(cur as? LessonUiState.Playing)?.copy(
						busy = false,
						verdict = res.verdict,
						hearts = res.stats.hearts,
						correctCount = cur.correctCount + if (res.verdict.correct) 1 else 0,
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
				val result = learningApi.completeSession(session, IdempotentRequest(UUID.randomUUID().toString()))
				_state.value = LessonUiState.Finished(result, total = s.lesson.exercises.size, savedOffline = false)
				// Post-lesson flush trigger: reconcile anything buffered from earlier failures.
				outbox.flush()
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
