package com.shikhi.app.ui.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikhi.app.R
import com.shikhi.app.ui.util.localized

@Composable
fun LessonScreen(onExit: () -> Unit, viewModel: LessonViewModel = hiltViewModel()) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	when (val s = state) {
		is LessonUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
			Text(stringResource(R.string.lesson_loading))
		}

		is LessonUiState.LoadError -> Column(
			Modifier.fillMaxSize().padding(24.dp),
			verticalArrangement = Arrangement.Center,
			horizontalAlignment = Alignment.CenterHorizontally,
		) {
			Text(stringResource(R.string.lesson_error), color = MaterialTheme.colorScheme.error)
			Spacer(Modifier.height(16.dp))
			TextButton(onClick = onExit) { Text(stringResource(R.string.lesson_back_to_course)) }
		}

		is LessonUiState.Finished -> LessonResultContent(s, onExit)

		is LessonUiState.Playing -> LessonContent(
			state = s,
			onExit = onExit,
			onSelectOption = viewModel::selectOption,
			onPlaceToken = viewModel::placeToken,
			onRemoveToken = viewModel::removeToken,
			onTextChange = viewModel::setTextAnswer,
			onCheck = viewModel::check,
			onNext = viewModel::next,
			onSkipUnsupported = viewModel::skipUnsupported,
		)
	}
}

@Composable
private fun LessonResultContent(s: LessonUiState.Finished, onExit: () -> Unit) {
	Column(
		Modifier.fillMaxSize().padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(stringResource(R.string.lesson_results_title), style = MaterialTheme.typography.headlineMedium)
		Spacer(Modifier.height(16.dp))
		Text(stringResource(R.string.lesson_score, s.result.score, s.total))
		Text(stringResource(R.string.lesson_xp_earned, s.result.xpEarned))
		Text(stringResource(R.string.lesson_hearts_left, s.result.stats.hearts))
		if (s.savedOffline) {
			Spacer(Modifier.height(12.dp))
			Text(
				stringResource(R.string.lesson_saved_offline),
				color = MaterialTheme.colorScheme.secondary,
				textAlign = TextAlign.Center,
			)
		}
		Spacer(Modifier.height(24.dp))
		Button(onClick = onExit) { Text(stringResource(R.string.lesson_back_to_course)) }
	}
}

/** Stateless playing surface — hoisted state keeps it directly UI-testable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonContent(
	state: LessonUiState.Playing,
	onExit: () -> Unit,
	onSelectOption: (String) -> Unit,
	onPlaceToken: (String) -> Unit,
	onRemoveToken: (String) -> Unit,
	onTextChange: (String) -> Unit,
	onCheck: () -> Unit,
	onNext: () -> Unit,
	onSkipUnsupported: () -> Unit,
) {
	val exercise = state.exercise
	val answered = state.verdict != null
	val isMcq = exercise.type == "MCQ"
	val isWordBank = exercise.type == "WORD_BANK"
	val isText = exercise.type in TEXT_TYPES
	val tokens = exercise.config.tokens.orEmpty()
	val canCheck = !state.busy && !answered && when {
		isMcq -> state.selectedOptionId != null
		isWordBank -> tokens.isNotEmpty() && state.placedTokenIds.size == tokens.size
		isText -> state.textAnswer.isNotBlank()
		else -> false
	}

	Column(
		Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
	) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			TextButton(onClick = onExit) { Text(stringResource(R.string.lesson_exit)) }
			Spacer(Modifier.weight(1f))
			Text(
				stringResource(R.string.lesson_progress, state.index + 1, state.lesson.exercises.size),
				style = MaterialTheme.typography.labelLarge,
			)
			Spacer(Modifier.weight(1f))
			Text(stringResource(R.string.stats_hearts, state.hearts))
		}
		Spacer(Modifier.height(16.dp))

		Text(
			exercise.prompt.localized(),
			style = MaterialTheme.typography.titleLarge,
			modifier = Modifier.testTag("lesson-prompt"),
		)
		Spacer(Modifier.height(20.dp))

		when {
			isMcq -> exercise.config.options.orEmpty().forEach { opt ->
				val selected = state.selectedOptionId == opt.id
				val colors = if (selected) {
					androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
						containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
					)
				} else {
					androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
				}
				OutlinedButton(
					onClick = { onSelectOption(opt.id) },
					enabled = !answered,
					colors = colors,
					modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("mcq-option-${opt.id}"),
				) {
					Text(opt.text.localized())
				}
			}

			isWordBank -> {
				FlowRow(
					Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("wordbank-sentence"),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
				) {
					if (state.placedTokenIds.isEmpty()) {
						Text(
							stringResource(R.string.lesson_word_bank_hint),
							style = MaterialTheme.typography.bodySmall,
						)
					} else {
						state.placedTokenIds.forEach { id ->
							val tk = tokens.find { it.id == id } ?: return@forEach
							Button(onClick = { onRemoveToken(id) }, enabled = !answered) { Text(tk.text.en) }
						}
					}
				}
				Spacer(Modifier.height(16.dp))
				FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					tokens.filter { it.id !in state.placedTokenIds }.forEach { tk ->
						OutlinedButton(
							onClick = { onPlaceToken(tk.id) },
							enabled = !answered,
							modifier = Modifier.testTag("token-${tk.id}"),
						) { Text(tk.text.en) }
					}
				}
			}

			isText -> androidx.compose.material3.OutlinedTextField(
				value = state.textAnswer,
				onValueChange = onTextChange,
				enabled = !answered,
				label = { Text(stringResource(R.string.lesson_answer_label)) },
				placeholder = { Text(stringResource(R.string.lesson_answer_placeholder)) },
				modifier = Modifier.fillMaxWidth().testTag("text-answer"),
			)

			else -> {
				// Fallback card for types without a renderer yet (PRD 21 §2).
				Surface(
					color = MaterialTheme.colorScheme.surfaceVariant,
					modifier = Modifier.fillMaxWidth(),
				) {
					Text(stringResource(R.string.lesson_unsupported), Modifier.padding(16.dp))
				}
				Spacer(Modifier.height(16.dp))
				Button(onClick = onSkipUnsupported, modifier = Modifier.fillMaxWidth()) {
					Text(stringResource(if (state.isLast) R.string.lesson_finish else R.string.lesson_next))
				}
			}
		}

		state.verdict?.let { verdict ->
			Spacer(Modifier.height(20.dp))
			val ok = verdict.correct
			Surface(
				color = if (ok) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
				modifier = Modifier.fillMaxWidth().testTag("verdict"),
			) {
				Column(Modifier.padding(16.dp)) {
					Text(
						stringResource(if (ok) R.string.lesson_correct else R.string.lesson_incorrect),
						style = MaterialTheme.typography.titleMedium,
						color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
					)
					verdict.feedback?.let { fb ->
						val text = fb.localized()
						if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyMedium)
					}
				}
			}
		}

		if (isMcq || isWordBank || isText) {
			Spacer(Modifier.height(24.dp))
			if (!answered) {
				Button(
					onClick = onCheck,
					enabled = canCheck,
					modifier = Modifier.fillMaxWidth().testTag("check-button"),
				) { Text(stringResource(R.string.lesson_check)) }
			} else {
				Button(
					onClick = onNext,
					enabled = !state.busy,
					modifier = Modifier.fillMaxWidth().testTag("next-button"),
				) {
					Text(stringResource(if (state.isLast) R.string.lesson_finish else R.string.lesson_next))
				}
			}
		}
	}
}
