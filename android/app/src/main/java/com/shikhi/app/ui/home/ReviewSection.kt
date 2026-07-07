package com.shikhi.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shikhi.app.R
import com.shikhi.app.data.api.ReviewApi
import com.shikhi.app.data.api.dto.ReviewItem
import com.shikhi.app.data.api.dto.ReviewResult
import com.shikhi.app.data.api.dto.ReviewResultsRequest
import com.shikhi.app.ui.util.localized
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
	private val reviewApi: ReviewApi,
) : ViewModel() {

	private val _items = MutableStateFlow<List<ReviewItem>>(emptyList())
	val items: StateFlow<List<ReviewItem>> = _items

	fun refresh() {
		viewModelScope.launch {
			_items.value = runCatching { reviewApi.due() }.getOrDefault(emptyList())
		}
	}

	/** Optimistically remove the card; record the outcome (reschedules server-side). */
	fun mark(exerciseId: String, correct: Boolean) {
		_items.update { it.filterNot { item -> item.exerciseId == exerciseId } }
		viewModelScope.launch {
			runCatching {
				reviewApi.results(ReviewResultsRequest(listOf(ReviewResult(exerciseId, correct))))
			}.onFailure { refresh() }
		}
	}
}

/** Spaced-repetition review (web ReviewPanel): self-graded recall cards. */
@Composable
fun ReviewSection(
	items: List<ReviewItem>,
	onMark: (String, Boolean) -> Unit,
) {
	if (items.isEmpty()) return
	Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
		Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge)
		Text(
			stringResource(R.string.review_due, items.size),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(8.dp))
		items.forEach { item ->
			Surface(
				color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
			) {
				Column(Modifier.padding(12.dp)) {
					Text(item.prompt.localized(), style = MaterialTheme.typography.bodyLarge)
					Spacer(Modifier.height(8.dp))
					Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						OutlinedButton(onClick = { onMark(item.exerciseId, true) }) {
							Text(stringResource(R.string.review_knew_it))
						}
						OutlinedButton(onClick = { onMark(item.exerciseId, false) }) {
							Text(stringResource(R.string.review_still_learning))
						}
					}
				}
			}
		}
	}
}
