package com.shikhi.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.PracticeResult
import com.shikhi.app.data.api.dto.PracticeRound
import com.shikhi.app.data.api.dto.SetLevelRequest
import com.shikhi.app.data.api.dto.Verdict
import com.shikhi.app.data.api.dto.nextLevel
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.connectivity.ConnectivityChecker
import com.shikhi.app.data.practice.LocalPracticeSource
import com.shikhi.app.data.practice.PracticePlaySource
import com.shikhi.app.data.practice.RemotePracticeSource
import com.shikhi.app.ui.util.Pronouncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject

/** Practice exercise types answered by picking an option (web PracticePlayer isMcq). */
private val CHOICE_TYPES = setOf("WORD_MEANING", "MEANING_WORD", "SENTENCE_GAP")

const val XP_PER_CORRECT = 10

/**
 * Continuous practice flow (E12, US-12.3), a direct port of PracticePlayer.tsx: exercises
 * come in rounds; each round ends on a summary with one-tap "keep going" and an optional
 * level-up offer. `strokes` is the round's matra — one entry per answered exercise.
 */
sealed interface PracticeUiState {
	data object Loading : PracticeUiState

	data object LoadError : PracticeUiState

	data class Playing(
		val round: PracticeRound,
		val index: Int,
		val strokes: List<Boolean> = emptyList(),
		val hearts: Int? = null,
		val selectedOptionId: String? = null,
		val placedTokenIds: List<String> = emptyList(),
		val textAnswer: String = "",
		val verdict: Verdict? = null,
		val busy: Boolean = false,
	) : PracticeUiState {
		val exercise get() = round.exercises[index]
		val isLastInRound get() = index == round.exercises.lastIndex
	}

	data class RoundDone(
		val round: PracticeRound,
		val strokes: List<Boolean>,
		val leveledUpTo: String? = null,
		val busy: Boolean = false,
	) : PracticeUiState {
		val correct get() = strokes.count { it }
		val offerLevelUpTo: String? get() = if (round.levelUpEligible && leveledUpTo == null) nextLevel(round.cefrLevel) else null
	}

	data class Finished(val result: PracticeResult) : PracticeUiState
}

/**
 * OF4 (docs/93-offline-learning-design.md §3.3): round generation/grading/completion are backed
 * by a [PracticePlaySource] resolved once per session start, exactly mirroring OF3's
 * [com.shikhi.app.ui.lesson.LessonViewModel] — online users get [RemotePracticeSource] (today's
 * server-graded behavior, now with outbox-buffered resilience on a failed `check()`, see that
 * class's doc); offline devices get [LocalPracticeSource] (bundled vocabulary + the local
 * practice engine). Pronunciation ([Pronouncer], OF2) is independent of the play source — it only
 * ever speaks text already rendered to the learner, never touches [PracticePlaySource].
 */
@HiltViewModel
class PracticeViewModel @Inject constructor(
	private val remoteSource: RemotePracticeSource,
	private val localSource: LocalPracticeSource,
	private val connectivity: ConnectivityChecker,
	private val authRepository: AuthRepository,
	private val progressApi: ProgressApi,
	private val pronouncer: Pronouncer,
	private val appScope: CoroutineScope,
) : ViewModel() {

	private val _state = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
	val state: StateFlow<PracticeUiState> = _state

	/** Web parity (`isSpeechSupported()`): the speaker controls hide themselves when this is false. */
	val speechAvailable: StateFlow<Boolean> = pronouncer.isAvailable

	fun pronounce(text: String) = pronouncer.speak(text)

	private var sessionId: String? = null

	/** Resolved once in [init] and reused for the whole session — never re-checked mid-play
	 * (§3.3), so a session's generation/grading source can't switch mid-answer. */
	private lateinit var source: PracticePlaySource

	/** True when [source] is [localSource] — hearts/stats have no local model (§7 non-goals), so
	 * the `stats()` network call for the hearts display is skipped entirely offline. */
	private var usingLocalSource: Boolean = false

	init {
		viewModelScope.launch {
			try {
				// A LocalGuest has no access token yet (OG1/OG2) — RemotePracticeSource would 401
				// regardless of device connectivity, so it must use the local engine until
				// GuestRegistrationWorker completes, not just when the device itself is offline.
				usingLocalSource = authRepository.session.value is SessionState.LocalGuest || !connectivity.isOnline()
				source = if (usingLocalSource) localSource else remoteSource
				val round = source.start()
				sessionId = round.sessionId
				_state.value = PracticeUiState.Playing(round, index = 0)
			} catch (e: Exception) {
				_state.value = PracticeUiState.LoadError
				return@launch
			}
			// Hearts show from the first exercise on, not only after the first answer.
			if (!usingLocalSource) {
				runCatching { progressApi.stats() }.onSuccess { stats ->
					_state.update { s ->
						if (s is PracticeUiState.Playing && s.hearts == null) s.copy(hearts = stats.hearts) else s
					}
				}
			}
		}
	}

	fun selectOption(optionId: String) {
		_state.update { s ->
			if (s is PracticeUiState.Playing && s.verdict == null) s.copy(selectedOptionId = optionId) else s
		}
	}

	fun placeToken(tokenId: String) {
		_state.update { s ->
			if (s is PracticeUiState.Playing && s.verdict == null && tokenId !in s.placedTokenIds) {
				s.copy(placedTokenIds = s.placedTokenIds + tokenId)
			} else {
				s
			}
		}
	}

	fun removeToken(tokenId: String) {
		_state.update { s ->
			if (s is PracticeUiState.Playing && s.verdict == null) s.copy(placedTokenIds = s.placedTokenIds - tokenId) else s
		}
	}

	fun setTextAnswer(value: String) {
		_state.update { s ->
			if (s is PracticeUiState.Playing && s.verdict == null) s.copy(textAnswer = value) else s
		}
	}

	fun check() {
		val s = _state.value as? PracticeUiState.Playing ?: return
		val session = sessionId ?: return
		if (s.busy || s.verdict != null) return
		val answer = when (s.exercise.type) {
			in CHOICE_TYPES -> {
				val selected = s.selectedOptionId ?: return
				buildJsonObject { put("selectedOptionId", selected) }
			}

			"SENTENCE_BUILD" -> {
				val tokens = s.exercise.config.tokens.orEmpty()
				if (s.placedTokenIds.size != tokens.size || tokens.isEmpty()) return
				val byId = tokens.associate { it.id to it.text.en }
				buildJsonObject {
					putJsonArray("tokenOrder") { s.placedTokenIds.forEach { add(byId[it] ?: "") } }
				}
			}

			"TYPE_WORD" -> {
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
					(cur as? PracticeUiState.Playing)?.copy(
						busy = false,
						verdict = outcome.verdict,
						hearts = outcome.hearts ?: cur.hearts,
						strokes = cur.strokes + outcome.verdict.correct,
					) ?: cur
				}
			} catch (e: Exception) {
				_state.update { cur ->
					(cur as? PracticeUiState.Playing)?.copy(
						busy = false,
						verdict = Verdict(correct = false),
						strokes = cur.strokes + false,
					) ?: cur
				}
			}
		}
	}

	fun next() {
		val s = _state.value as? PracticeUiState.Playing ?: return
		if (s.busy) return
		_state.value = if (!s.isLastInRound) {
			s.copy(
				index = s.index + 1,
				selectedOptionId = null,
				placedTokenIds = emptyList(),
				textAnswer = "",
				verdict = null,
			)
		} else {
			PracticeUiState.RoundDone(s.round, s.strokes)
		}
	}

	fun keepGoing() {
		val s = _state.value as? PracticeUiState.RoundDone ?: return
		val session = sessionId ?: return
		if (s.busy) return
		_state.value = s.copy(busy = true)
		viewModelScope.launch {
			try {
				val round = source.nextRound(session)
				_state.value = PracticeUiState.Playing(round, index = 0, hearts = null)
				if (!usingLocalSource) {
					runCatching { progressApi.stats() }.onSuccess { stats ->
						_state.update { cur ->
							if (cur is PracticeUiState.Playing && cur.hearts == null) cur.copy(hearts = stats.hearts) else cur
						}
					}
				}
			} catch (e: Exception) {
				_state.value = PracticeUiState.LoadError
			}
		}
	}

	fun acceptLevelUp() {
		val s = _state.value as? PracticeUiState.RoundDone ?: return
		val upTo = s.offerLevelUpTo ?: return
		viewModelScope.launch {
			runCatching { progressApi.setLevel(SetLevelRequest(upTo)) }.onSuccess { stats ->
				_state.update { cur ->
					(cur as? PracticeUiState.RoundDone)?.copy(leveledUpTo = stats.cefrLevel) ?: cur
				}
			}
		}
	}

	fun finish() {
		val session = sessionId ?: return
		val s = _state.value
		if (s is PracticeUiState.RoundDone && s.busy) return
		if (s is PracticeUiState.RoundDone) _state.value = s.copy(busy = true)
		viewModelScope.launch {
			try {
				val result = source.complete(session)
				_state.value = PracticeUiState.Finished(result)
			} catch (e: Exception) {
				_state.value = PracticeUiState.LoadError
			}
		}
	}

	/**
	 * Leaving mid-session still finalizes it (idempotent server-side, web parity).
	 * onCleared covers every exit path — the ✕ button, system back, and app close — and
	 * runs in the app scope because viewModelScope is already cancelled at that point.
	 */
	override fun onCleared() {
		val session = sessionId ?: return
		if (_state.value is PracticeUiState.Finished) return
		appScope.launch {
			runCatching { source.complete(session) }
		}
	}
}
