package com.shikhi.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
fun HomeScreen(onOpenLesson: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
	val ui by viewModel.state.collectAsStateWithLifecycle()

	// Re-pull stats + progress each time home comes (back) on screen — the web parent
	// bumps a refreshKey after a lesson finishes for the same reason.
	LaunchedEffect(Unit) { viewModel.refresh() }

	Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
		Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
			Text(
				stringResource(R.string.app_name),
				style = MaterialTheme.typography.headlineSmall,
				color = MaterialTheme.colorScheme.primary,
			)
			Spacer(Modifier.weight(1f))
			TextButton(onClick = viewModel::logout) { Text(stringResource(R.string.log_out)) }
		}

		ui.stats?.let { stats ->
			Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				Text(stringResource(R.string.stats_xp, stats.xp), style = MaterialTheme.typography.bodyLarge)
				Text(stringResource(R.string.stats_streak, stats.currentStreak), style = MaterialTheme.typography.bodyLarge)
				Text(stringResource(R.string.stats_hearts, stats.hearts), style = MaterialTheme.typography.bodyLarge)
			}
		}

		if (ui.health != BackendHealth.ONLINE) HealthBadge(ui.health)

		Spacer(Modifier.height(8.dp))
		Text(stringResource(R.string.curriculum_title), style = MaterialTheme.typography.titleLarge)
		Spacer(Modifier.height(8.dp))

		when {
			ui.curriculumLoading -> Text(stringResource(R.string.curriculum_loading))
			ui.curriculumError -> Text(stringResource(R.string.curriculum_error), color = MaterialTheme.colorScheme.error)
			ui.tree != null && ui.tree!!.levels.isEmpty() -> Text(stringResource(R.string.curriculum_empty))
			else -> LazyColumn(Modifier.fillMaxSize()) {
				ui.tree?.levels?.forEach { level ->
					item(key = level.id) {
						Text(
							level.title.localized(),
							style = MaterialTheme.typography.titleMedium,
							modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
						)
					}
					level.units.forEach { unit ->
						item(key = unit.id) {
							Text(
								unit.title.localized(),
								style = MaterialTheme.typography.labelLarge,
								color = MaterialTheme.colorScheme.secondary,
								modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
							)
						}
						items(unit.lessons, key = { it.id }) { lesson ->
							LessonRow(lesson, onOpenLesson)
						}
					}
				}
			}
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
