package com.shikhi.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.R
import com.shikhi.app.data.api.dto.VocabularyEntry
import com.shikhi.app.data.content.CachedContentRepository
import com.shikhi.app.ui.util.Pronouncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Web VocabularyBrowser parity: 40 rows per page so a full band stays snappy. */
private const val PAGE_SIZE = 40

val VOCAB_LEVELS = listOf("A1", "A2", "B1", "B2", "C1")

data class VocabUiState(
	val open: Boolean = false,
	val level: String = "A1",
	val entries: List<VocabularyEntry> = emptyList(),
	val loading: Boolean = false,
	val error: Boolean = false,
	val query: String = "",
	val page: Int = 1,
) {
	val filtered: List<VocabularyEntry>
		get() {
			val q = query.trim().lowercase()
			if (q.isEmpty()) return entries
			return entries.filter { it.headword.lowercase().contains(q) || it.bnGloss.contains(query.trim()) }
		}

	val totalPages get() = max(1, ceil(filtered.size / PAGE_SIZE.toDouble()).toInt())
	val currentPage get() = min(page, totalPages)
	val pageStart get() = (currentPage - 1) * PAGE_SIZE
	val shown get() = filtered.drop(pageStart).take(PAGE_SIZE)
}

@HiltViewModel
class VocabViewModel @Inject constructor(
	private val content: CachedContentRepository,
	private val pronouncer: Pronouncer,
) : ViewModel() {

	private val _state = MutableStateFlow(VocabUiState())
	val state: StateFlow<VocabUiState> = _state

	/** Web parity (`isSpeechSupported()`): the speaker button hides itself when this is false. */
	val speechAvailable: StateFlow<Boolean> = pronouncer.isAvailable

	fun pronounce(headword: String) = pronouncer.speak(headword)

	fun toggleOpen() {
		val opening = !_state.value.open
		_state.update { it.copy(open = opening) }
		if (opening && _state.value.entries.isEmpty()) load()
	}

	fun setLevel(level: String) {
		if (level == _state.value.level) return
		_state.update { it.copy(level = level, page = 1) }
		load()
	}

	fun setQuery(query: String) = _state.update { it.copy(query = query, page = 1) }

	fun prevPage() = _state.update { it.copy(page = max(1, it.currentPage - 1)) }

	fun nextPage() = _state.update { it.copy(page = min(it.totalPages, it.currentPage + 1)) }

	private fun load() {
		val level = _state.value.level
		_state.update { it.copy(loading = true, error = false) }
		viewModelScope.launch {
			val data = content.vocabulary(level)
			_state.update {
				if (data != null) it.copy(entries = data.value, loading = false) else it.copy(loading = false, error = true)
			}
		}
	}
}

/** Oxford-5000 word browser (web VocabularyBrowser): CEFR tabs, search, paging. */
@Composable
fun VocabularySection(
	s: VocabUiState,
	onToggle: () -> Unit,
	onLevel: (String) -> Unit,
	onQuery: (String) -> Unit,
	onPrev: () -> Unit,
	onNext: () -> Unit,
	speechAvailable: Boolean = false,
	onPronounce: (String) -> Unit = {},
) {
	Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
			Text(stringResource(R.string.vocab_title), style = MaterialTheme.typography.titleLarge)
			Spacer(Modifier.weight(1f))
			TextButton(onClick = onToggle) {
				Text(stringResource(if (s.open) R.string.vocab_hide else R.string.vocab_show))
			}
		}
		if (!s.open) return@Column

		Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
			VOCAB_LEVELS.forEach { lv ->
				val active = lv == s.level
				OutlinedButton(
					onClick = { onLevel(lv) },
					colors = if (active) {
						ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
					} else {
						ButtonDefaults.outlinedButtonColors()
					},
					contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
					modifier = Modifier.weight(1f),
				) { Text(lv) }
			}
		}
		Spacer(Modifier.height(8.dp))
		OutlinedTextField(
			value = s.query,
			onValueChange = onQuery,
			placeholder = { Text(stringResource(R.string.vocab_search_placeholder)) },
			modifier = Modifier.fillMaxWidth(),
		)
		Spacer(Modifier.height(8.dp))

		when {
			s.loading -> Text(stringResource(R.string.vocab_loading))
			s.error -> Text(stringResource(R.string.vocab_error), color = MaterialTheme.colorScheme.error)
			s.entries.isEmpty() -> Text(stringResource(R.string.vocab_empty))
			else -> {
				Text(
					stringResource(
						R.string.vocab_count,
						if (s.filtered.isEmpty()) 0 else s.pageStart + 1,
						min(s.pageStart + PAGE_SIZE, s.filtered.size),
						s.filtered.size,
					),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.secondary,
				)
				Spacer(Modifier.height(4.dp))
				s.shown.forEach { e ->
					Surface(
						color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
						shape = RoundedCornerShape(10.dp),
						modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
					) {
						Column(Modifier.padding(10.dp)) {
							Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
								Text(e.headword, style = MaterialTheme.typography.titleMedium)
								if (speechAvailable) {
									IconButton(
										onClick = { onPronounce(e.headword) },
										modifier = Modifier.size(28.dp),
									) {
										Icon(
											Icons.AutoMirrored.Filled.VolumeUp,
											contentDescription = stringResource(R.string.vocab_pronounce, e.headword),
											modifier = Modifier.size(16.dp),
										)
									}
								}
								Spacer(Modifier.width(8.dp))
								Text(e.partOfSpeech, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
								e.senseLabel?.let {
									Spacer(Modifier.width(4.dp))
									Text("($it)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
								}
							}
							Text(e.bnGloss, style = MaterialTheme.typography.bodyMedium)
							e.exampleEn?.let { en ->
								Spacer(Modifier.height(4.dp))
								Text(en, style = MaterialTheme.typography.bodySmall)
								e.exampleBn?.let { bn ->
									Text(bn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
								}
							}
						}
					}
				}
				if (s.totalPages > 1) {
					Row(
						Modifier.fillMaxWidth().padding(top = 8.dp),
						verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
					) {
						OutlinedButton(onClick = onPrev, enabled = s.currentPage > 1) {
							Text(stringResource(R.string.vocab_prev))
						}
						Spacer(Modifier.weight(1f))
						Text(
							stringResource(R.string.vocab_page, s.currentPage, s.totalPages),
							style = MaterialTheme.typography.bodySmall,
						)
						Spacer(Modifier.weight(1f))
						OutlinedButton(onClick = onNext, enabled = s.currentPage < s.totalPages) {
							Text(stringResource(R.string.vocab_next))
						}
					}
				}
			}
		}
	}
}
