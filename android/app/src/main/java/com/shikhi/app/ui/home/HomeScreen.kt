package com.shikhi.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikhi.app.R
import com.shikhi.app.data.api.dto.LessonNode
import com.shikhi.app.ui.util.localized

@Composable
fun HomeScreen(
	onOpenLesson: (String) -> Unit,
	onStartPractice: () -> Unit,
	onOpenProfile: () -> Unit,
	viewModel: HomeViewModel = hiltViewModel(),
	reviewViewModel: ReviewViewModel = hiltViewModel(),
	vocabViewModel: VocabViewModel = hiltViewModel(),
) {
	val ui by viewModel.state.collectAsStateWithLifecycle()
	val reviewItems by reviewViewModel.items.collectAsStateWithLifecycle()
	val vocab by vocabViewModel.state.collectAsStateWithLifecycle()
	val vocabSpeechAvailable by vocabViewModel.speechAvailable.collectAsStateWithLifecycle()

	// Re-pull stats + progress + due reviews each time home comes (back) on screen — the
	// web parent bumps a refreshKey after a lesson/practice finishes for the same reason.
	LaunchedEffect(Unit) {
		viewModel.refresh()
		reviewViewModel.refresh()
	}

	LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
		item(key = "header") {
			Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
				Text(
					stringResource(R.string.app_name),
					style = MaterialTheme.typography.headlineSmall,
					color = MaterialTheme.colorScheme.primary,
				)
				Spacer(Modifier.weight(1f))
				// Logout lives in the profile screen now (PRD 21 §8) — this is just the entry point.
				IconButton(onClick = onOpenProfile) {
					Icon(Icons.Filled.AccountCircle, contentDescription = stringResource(R.string.profile_open))
				}
			}
		}

		item(key = "stats") {
			ui.stats?.let { stats ->
				Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
					Text(stringResource(R.string.stats_xp, stats.xp), style = MaterialTheme.typography.bodyLarge)
					Text(stringResource(R.string.stats_streak, stats.currentStreak), style = MaterialTheme.typography.bodyLarge)
					Text(stringResource(R.string.stats_hearts, stats.hearts), style = MaterialTheme.typography.bodyLarge)
				}
			}
			if (ui.health != BackendHealth.ONLINE) HealthBadge(ui.health)
			if (ui.fromCache) OfflineCopyBanner()
		}

		if (ui.isGuest) {
			item(key = "guest-banner") {
				Box(Modifier.padding(vertical = 8.dp)) { GuestBanner() }
			}
		}

		item(key = "practice-hero") {
			Box(Modifier.padding(vertical = 8.dp)) {
				PracticeHero(
					level = ui.stats?.cefrLevel ?: "A1",
					streak = ui.stats?.currentStreak ?: 0,
					saving = ui.savingLevel,
					error = ui.levelError,
					onPickLevel = viewModel::setLevel,
					onStart = onStartPractice,
				)
			}
		}

		item(key = "review") {
			ReviewSection(items = reviewItems, onMark = reviewViewModel::mark)
		}

		// Curriculum section hidden from the Android client — the guided lesson tree is
		// intentionally not surfaced here. Practice + review + vocabulary remain the home surface.

		item(key = "vocabulary") {
			VocabularySection(
				s = vocab,
				onToggle = vocabViewModel::toggleOpen,
				onLevel = vocabViewModel::setLevel,
				onQuery = vocabViewModel::setQuery,
				onPrev = vocabViewModel::prevPage,
				onNext = vocabViewModel::nextPage,
				speechAvailable = vocabSpeechAvailable,
				onPronounce = vocabViewModel::pronounce,
			)
			Spacer(Modifier.height(24.dp))
		}
	}
}

@Composable
private fun LessonRow(lesson: LessonNode, onOpenLesson: (String) -> Unit) {
	val done = lesson.status == "COMPLETED"
	val mark = when {
		done -> "✓ "
		lesson.locked -> "🔒 "
		else -> ""
	}
	OutlinedButton(
		onClick = { onOpenLesson(lesson.id) },
		enabled = !lesson.locked,
		modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
	) {
		Text(
			mark + lesson.title.localized(),
			color = if (done) MaterialTheme.colorScheme.primary else Color.Unspecified,
		)
	}
}

/** Shown when the curriculum on screen came from the Room cache, not the network (NFR-AN4). */
@Composable
private fun OfflineCopyBanner() {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(vertical = 4.dp)
			.background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
			.padding(horizontal = 10.dp, vertical = 6.dp),
	) {
		Text(
			stringResource(R.string.offline_copy),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}

@Composable
private fun HealthBadge(health: BackendHealth) {
	val (color, label) = when (health) {
		BackendHealth.CHECKING -> Color(0xFF9E9E9E) to R.string.health_checking
		BackendHealth.ONLINE -> Color(0xFF2E7D32) to R.string.health_online
		BackendHealth.WARMING -> Color(0xFFF9A825) to R.string.health_warming
		BackendHealth.OFFLINE -> Color(0xFFC62828) to R.string.health_offline
	}
	Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
		Box(Modifier.size(10.dp).background(color, CircleShape))
		Spacer(Modifier.width(8.dp))
		Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
	}
}
