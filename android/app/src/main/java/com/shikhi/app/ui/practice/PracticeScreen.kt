package com.shikhi.app.ui.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikhi.app.R
import com.shikhi.app.ui.util.localized

/** Pulls the curly-quoted headword out of a WORD_MEANING prompt (e.g. `What does “X” mean?`) —
 * mirrors the web's `quotedWord()` (frontend/src/components/PracticePlayer.tsx). The Kotlin
 * [com.shikhi.app.data.api.dto.PracticeExercise] DTO carries no structured headword field (the
 * generator only ever puts the word into the rendered prompt/options text, matching the
 * backend's `PracticeExerciseView`), so extracting it from the already-visible prompt text is the
 * only option here too — it reveals nothing the learner hasn't already been shown. */
private val QUOTED_WORD = Regex("“([^”]+)”")

private fun quotedWord(text: String): String? = QUOTED_WORD.find(text)?.groupValues?.get(1)

/** A wrong-answer reveal reads `Correct answer: word — বাংলা gloss` or a plain English sentence;
 * either way the part before the first em dash is clean, speakable English (web `revealWord()`). */
private fun revealWord(feedbackEn: String): String =
	feedbackEn.removePrefix("Correct answer: ").split(" — ").first().trim()

@Composable
fun PracticeScreen(onExit: () -> Unit, viewModel: PracticeViewModel = hiltViewModel()) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val speechAvailable by viewModel.speechAvailable.collectAsStateWithLifecycle()

	// Mid-session finalization happens in the ViewModel's onCleared, covering the ✕
	// button, system back, and app close alike.
	when (val s = state) {
		is PracticeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			Text(stringResource(R.string.practice_loading))
		}

		is PracticeUiState.LoadError -> Column(
			Modifier.fillMaxSize().padding(24.dp),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(stringResource(R.string.practice_error), color = MaterialTheme.colorScheme.error)
			Spacer(Modifier.height(16.dp))
			TextButton(onClick = onExit) { Text(stringResource(R.string.practice_back_home)) }
		}

		is PracticeUiState.Finished -> Column(
			Modifier.fillMaxSize().padding(24.dp),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(stringResource(R.string.practice_results_title), style = MaterialTheme.typography.headlineMedium)
			Spacer(Modifier.height(16.dp))
			Text(stringResource(R.string.practice_session_score, s.result.correctCount, s.result.totalCount))
			Text(stringResource(R.string.practice_xp_earned, s.result.xpEarned))
			Spacer(Modifier.height(24.dp))
			Button(onClick = onExit) { Text(stringResource(R.string.practice_back_home)) }
		}

		is PracticeUiState.RoundDone -> RoundDoneContent(
			s = s,
			onKeepGoing = viewModel::keepGoing,
			onFinish = viewModel::finish,
			onAcceptLevelUp = viewModel::acceptLevelUp,
		)

		is PracticeUiState.Playing -> PracticeContent(
			state = s,
			onExit = onExit,
			onSelectOption = viewModel::selectOption,
			onPlaceToken = viewModel::placeToken,
			onRemoveToken = viewModel::removeToken,
			onTextChange = viewModel::setTextAnswer,
			onCheck = viewModel::check,
			onNext = viewModel::next,
			speechAvailable = speechAvailable,
			onPronounce = viewModel::pronounce,
		)
	}
}

@Composable
private fun RoundDoneContent(
	s: PracticeUiState.RoundDone,
	onKeepGoing: () -> Unit,
	onFinish: () -> Unit,
	onAcceptLevelUp: () -> Unit,
) {
	Column(
		Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(stringResource(R.string.practice_round_title), style = MaterialTheme.typography.headlineMedium)
		Spacer(Modifier.height(16.dp))
		Text(stringResource(R.string.practice_round_score, s.correct, s.strokes.size))
		Text(stringResource(R.string.practice_xp_earned, s.correct * XP_PER_CORRECT))

		s.offerLevelUpTo?.let { upTo ->
			Spacer(Modifier.height(20.dp))
			Surface(
				color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
				shape = RoundedCornerShape(12.dp),
			) {
				Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
					Text(stringResource(R.string.practice_level_up_offer, upTo))
					Spacer(Modifier.height(8.dp))
					OutlinedButton(onClick = onAcceptLevelUp) {
						Text(stringResource(R.string.practice_level_up_accept, upTo))
					}
				}
			}
		}
		s.leveledUpTo?.let { level ->
			Spacer(Modifier.height(12.dp))
			Text(
				stringResource(R.string.practice_level_up_done, level),
				color = MaterialTheme.colorScheme.primary,
			)
		}

		Spacer(Modifier.height(24.dp))
		Button(onClick = onKeepGoing, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) {
			Text(stringResource(R.string.practice_keep_going))
		}
		Spacer(Modifier.height(8.dp))
		TextButton(onClick = onFinish, enabled = !s.busy) {
			Text(stringResource(R.string.practice_finish))
		}
	}
}

/** Small speaker icon (mirrors `VocabularySection`'s pronounce control) — only ever rendered when
 * the caller has already checked [PracticeViewModel.speechAvailable]. */
@Composable
private fun SpeakIcon(word: String, onPronounce: (String) -> Unit, modifier: Modifier = Modifier) {
	IconButton(onClick = { onPronounce(word) }, modifier = modifier.size(28.dp)) {
		Icon(
			Icons.AutoMirrored.Filled.VolumeUp,
			contentDescription = stringResource(R.string.vocab_pronounce, word),
			modifier = Modifier.size(16.dp),
		)
	}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PracticeContent(
	state: PracticeUiState.Playing,
	onExit: () -> Unit,
	onSelectOption: (String) -> Unit,
	onPlaceToken: (String) -> Unit,
	onRemoveToken: (String) -> Unit,
	onTextChange: (String) -> Unit,
	onCheck: () -> Unit,
	onNext: () -> Unit,
	speechAvailable: Boolean = false,
	onPronounce: (String) -> Unit = {},
) {
	val exercise = state.exercise
	val answered = state.verdict != null
	val isChoice = exercise.type in setOf("WORD_MEANING", "MEANING_WORD", "SENTENCE_GAP")
	val isBuild = exercise.type == "SENTENCE_BUILD"
	val isType = exercise.type == "TYPE_WORD"
	// Options are only real English words (safe to pronounce as-is) for these two types —
	// WORD_MEANING's MCQ options are Bengali meanings, which an en-US voice would mangle.
	val optionsAreEnglish = exercise.type == "MEANING_WORD" || exercise.type == "SENTENCE_GAP"
	val promptWord = if (exercise.type == "WORD_MEANING") quotedWord(exercise.prompt.en) else null
	val tokens = exercise.config.tokens.orEmpty()
	val canCheck = !state.busy && !answered && when {
		isChoice -> state.selectedOptionId != null
		isBuild -> tokens.isNotEmpty() && state.placedTokenIds.size == tokens.size
		isType -> state.textAnswer.isNotBlank()
		else -> false
	}

	Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			TextButton(onClick = onExit) { Text(stringResource(R.string.lesson_exit)) }
			Spacer(Modifier.width(8.dp))
			// The round's matra: one stroke per exercise, ink for right, clay for wrong.
			Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
				state.round.exercises.forEachIndexed { i, _ ->
					val color = when {
						i < state.strokes.size && state.strokes[i] -> MaterialTheme.colorScheme.primary
						i < state.strokes.size -> MaterialTheme.colorScheme.error
						i == state.index -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
						else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
					}
					Box(
						Modifier
							.weight(1f)
							.height(6.dp)
							.background(color, RoundedCornerShape(3.dp)),
					)
				}
			}
			Spacer(Modifier.width(8.dp))
			Text(state.hearts?.let { stringResource(R.string.stats_hearts, it) } ?: "")
		}
		Spacer(Modifier.height(16.dp))

		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(
				exercise.prompt.localized(),
				style = MaterialTheme.typography.titleLarge,
				modifier = Modifier.testTag("practice-prompt"),
			)
			if (speechAvailable && promptWord != null) {
				Spacer(Modifier.width(4.dp))
				SpeakIcon(promptWord, onPronounce)
			}
		}
		if (exercise.type == "SENTENCE_GAP" && exercise.config.contextBn != null) {
			Spacer(Modifier.height(8.dp))
			Text(exercise.config.contextBn!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
		}
		if (isType && exercise.config.partOfSpeech != null) {
			Spacer(Modifier.height(8.dp))
			Text(exercise.config.partOfSpeech!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
		}
		Spacer(Modifier.height(20.dp))

		when {
			isChoice -> exercise.config.options.orEmpty().forEach { opt ->
				val selected = state.selectedOptionId == opt.id
				val colors = if (selected) {
					ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
				} else {
					ButtonDefaults.outlinedButtonColors()
				}
				Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
					OutlinedButton(
						onClick = { onSelectOption(opt.id) },
						enabled = !answered,
						colors = colors,
						modifier = Modifier.weight(1f),
					) {
						Text(opt.text.localized())
					}
					if (speechAvailable && optionsAreEnglish) {
						Spacer(Modifier.width(4.dp))
						SpeakIcon(opt.text.en, onPronounce)
					}
				}
			}

			isType -> OutlinedTextField(
				value = state.textAnswer,
				onValueChange = onTextChange,
				enabled = !answered,
				label = { Text(stringResource(R.string.lesson_answer_label)) },
				placeholder = { Text(stringResource(R.string.lesson_answer_placeholder)) },
				modifier = Modifier.fillMaxWidth(),
			)

			isBuild -> {
				exercise.config.targetBn?.let { target ->
					Text(target, style = MaterialTheme.typography.bodyLarge)
					Spacer(Modifier.height(12.dp))
				}
				FlowRow(
					Modifier.fillMaxWidth().heightIn(min = 56.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					if (state.placedTokenIds.isEmpty()) {
						Text(stringResource(R.string.lesson_word_bank_hint), style = MaterialTheme.typography.bodySmall)
					} else {
						state.placedTokenIds.forEach { id ->
							val tk = tokens.find { it.id == id } ?: return@forEach
							Row(verticalAlignment = Alignment.CenterVertically) {
								Button(onClick = { onRemoveToken(id) }, enabled = !answered) { Text(tk.text.en) }
								if (speechAvailable) SpeakIcon(tk.text.en, onPronounce)
							}
						}
					}
				}
				if (speechAvailable && state.placedTokenIds.size == tokens.size && tokens.isNotEmpty()) {
					Spacer(Modifier.height(8.dp))
					Row(verticalAlignment = Alignment.CenterVertically) {
						SpeakIcon(
							state.placedTokenIds.mapNotNull { id -> tokens.find { it.id == id }?.text?.en }.joinToString(" "),
							onPronounce,
						)
						Spacer(Modifier.width(4.dp))
						Text(stringResource(R.string.practice_listen_sentence), style = MaterialTheme.typography.bodySmall)
					}
				}
				Spacer(Modifier.height(16.dp))
				FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					tokens.filter { it.id !in state.placedTokenIds }.forEach { tk ->
						Row(verticalAlignment = Alignment.CenterVertically) {
							OutlinedButton(onClick = { onPlaceToken(tk.id) }, enabled = !answered) { Text(tk.text.en) }
							if (speechAvailable) SpeakIcon(tk.text.en, onPronounce)
						}
					}
				}
			}
		}

		state.verdict?.let { verdict ->
			Spacer(Modifier.height(20.dp))
			val ok = verdict.correct
			Surface(
				color = if (ok) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(Modifier.padding(16.dp)) {
					Text(
						stringResource(if (ok) R.string.lesson_correct else R.string.lesson_incorrect),
						style = MaterialTheme.typography.titleMedium,
						color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
					)
					// The body carries the reveal on wrong answers only (web parity).
					if (!ok) {
						verdict.feedback?.let { fb ->
							val text = fb.localized()
							if (text.isNotBlank()) {
								Row(verticalAlignment = Alignment.CenterVertically) {
									Text(text, style = MaterialTheme.typography.bodyMedium)
									if (speechAvailable && fb.en.isNotBlank()) {
										Spacer(Modifier.width(4.dp))
										SpeakIcon(revealWord(fb.en), onPronounce)
									}
								}
							}
						}
					}
				}
			}
		}

		Spacer(Modifier.height(24.dp))
		if (!answered) {
			Button(onClick = onCheck, enabled = canCheck, modifier = Modifier.fillMaxWidth()) {
				Text(stringResource(R.string.lesson_check))
			}
		} else {
			Button(onClick = onNext, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
				Text(
					stringResource(
						if (state.isLastInRound) R.string.practice_to_summary else R.string.lesson_next,
					),
				)
			}
		}
	}
}
