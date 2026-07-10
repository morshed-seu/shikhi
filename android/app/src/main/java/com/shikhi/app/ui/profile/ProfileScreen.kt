package com.shikhi.app.ui.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikhi.app.R
import com.shikhi.app.data.api.dto.DashboardResponse
import com.shikhi.app.data.api.dto.WordMasteryEntry
import com.shikhi.app.ui.home.GuestBanner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// A ratio between 0 and this floor still renders as a visible sliver, matching the web
// mastery bars (frontend/src/components/MasteryBars.tsx) so "barely started" doesn't look
// identical to "untouched".
private const val MIN_VISIBLE_FRACTION = 0.015f

/**
 * Profile & dashboard (E13/MD3): profile card (name/locale/identity), dashboard stats grid,
 * per-band mastery bars, and account actions. Logout lives here now — it moved out of the
 * home header to keep the practice-first home uncluttered (PRD 21 §8).
 */
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
	val ui by viewModel.state.collectAsStateWithLifecycle()
	val context = LocalContext.current
	val exportSubject = stringResource(R.string.profile_export_subject)

	LaunchedEffect(ui.exportPayload) {
		val payload = ui.exportPayload ?: return@LaunchedEffect
		val intent = Intent(Intent.ACTION_SEND).apply {
			type = "application/json"
			putExtra(Intent.EXTRA_TEXT, payload)
			putExtra(Intent.EXTRA_SUBJECT, exportSubject)
		}
		context.startActivity(Intent.createChooser(intent, exportSubject))
		viewModel.consumeExport()
	}

	Column(
		Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			// navigationBarsPadding after verticalScroll: extra scrollable content padding,
			// so the delete/logout actions can scroll clear of the gesture bar.
			.navigationBarsPadding()
			.padding(horizontal = 20.dp),
	) {
		// statusBarsPadding: targetSdk 36 forces edge-to-edge on Android 15+, which would
		// otherwise leave the back button under the status bar (E14 precedent).
		Row(
			Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = onBack) {
				Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.profile_back))
			}
			Spacer(Modifier.width(4.dp))
			Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall)
		}

		when {
			ui.loading && ui.dashboard == null -> {
				Spacer(Modifier.height(24.dp))
				Text(stringResource(R.string.profile_loading))
			}
			ui.error -> {
				Spacer(Modifier.height(24.dp))
				Text(stringResource(R.string.profile_error), color = MaterialTheme.colorScheme.error)
			}
			else -> ui.dashboard?.let { dashboard ->
				Spacer(Modifier.height(12.dp))
				if (ui.fromCache) OfflineCopyBanner()

				ProfileCard(ui = ui, viewModel = viewModel)
				Spacer(Modifier.height(16.dp))
				StatsGrid(dashboard)
				Spacer(Modifier.height(16.dp))
				MasteryBars(dashboard.wordMastery)
				Spacer(Modifier.height(16.dp))

				if (ui.isGuest) {
					Text(stringResource(R.string.profile_actions_title), style = MaterialTheme.typography.titleMedium)
					Spacer(Modifier.height(8.dp))
					GuestBanner()
				} else {
					AccountActions(ui = ui, viewModel = viewModel)
				}

				Spacer(Modifier.height(16.dp))
				TextButton(onClick = viewModel::logout) { Text(stringResource(R.string.log_out)) }
				Spacer(Modifier.height(24.dp))
			}
		}
	}
}

@Composable
private fun ProfileCard(ui: ProfileUiState, viewModel: ProfileViewModel) {
	val user = ui.user
	val language = LocalConfiguration.current.locales[0]?.language ?: "bn"
	val email = ui.identities.find { it.provider == "EMAIL" }

	Surface(
		color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
		shape = RoundedCornerShape(16.dp),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(Modifier.padding(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				if (ui.editingName) {
					OutlinedTextField(
						value = ui.nameDraft,
						onValueChange = viewModel::setNameDraft,
						label = { Text(stringResource(R.string.auth_display_name)) },
						singleLine = true,
						modifier = Modifier.weight(1f),
					)
					Spacer(Modifier.width(8.dp))
					TextButton(onClick = viewModel::saveName, enabled = !ui.savingName) {
						Text(stringResource(R.string.profile_save))
					}
					TextButton(onClick = viewModel::cancelEditName) {
						Text(stringResource(R.string.profile_cancel))
					}
				} else {
					Text(
						user?.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.auth_learner),
						style = MaterialTheme.typography.titleLarge,
						modifier = Modifier.weight(1f),
					)
					if (ui.editsEnabled) {
						IconButton(onClick = viewModel::startEditName) {
							Icon(
								Icons.Filled.Edit,
								contentDescription = stringResource(R.string.profile_edit_name),
								tint = MaterialTheme.colorScheme.primary,
							)
						}
					}
				}
			}

			Spacer(Modifier.height(6.dp))
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				ui.dashboard?.stats?.cefrLevel?.let { level ->
					Box(
						Modifier
							.clip(RoundedCornerShape(6.dp))
							.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
							.padding(horizontal = 8.dp, vertical = 2.dp),
					) {
						Text(level, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
					}
				}
				if (ui.isGuest) {
					Text(
						stringResource(R.string.guest_badge),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.secondary,
					)
				} else if (email != null) {
					Text(email.maskedRef, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
				}
			}

			formatJoined(user?.joinedAt, language)?.let { joined ->
				Spacer(Modifier.height(4.dp))
				Text(
					stringResource(R.string.profile_joined, joined),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.secondary,
				)
			}

			Spacer(Modifier.height(12.dp))
			Text(stringResource(R.string.profile_language), style = MaterialTheme.typography.labelLarge)
			Spacer(Modifier.height(4.dp))
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				FilterChip(
					selected = language != "en",
					onClick = { viewModel.setLocale("bn") },
					enabled = ui.editsEnabled && !ui.savingLocale,
					label = { Text("বাংলা") },
				)
				FilterChip(
					selected = language == "en",
					onClick = { viewModel.setLocale("en") },
					enabled = ui.editsEnabled && !ui.savingLocale,
					label = { Text("English") },
				)
			}
		}
	}
}

/** Instant → a locale-formatted date, or null if [joinedAt] is absent/unparseable. */
private fun formatJoined(joinedAt: String?, language: String): String? {
	if (joinedAt.isNullOrBlank()) return null
	val locale = if (language == "en") Locale.ENGLISH else Locale.forLanguageTag("bn-BD")
	return runCatching {
		val date = Instant.parse(joinedAt).atZone(ZoneId.systemDefault()).toLocalDate()
		DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
	}.getOrNull()
}

@Composable
private fun StatsGrid(dashboard: DashboardResponse) {
	val accuracy = if (dashboard.totalAnswered > 0) {
		"${(dashboard.totalCorrect * 100) / dashboard.totalAnswered}%"
	} else {
		"—"
	}
	val stats = dashboard.stats
	val tiles = listOf(
		stringResource(R.string.stats_label_xp) to stats.xp.toString(),
		stringResource(R.string.stats_label_streak) to stats.currentStreak.toString(),
		stringResource(R.string.profile_stats_longest_streak) to stats.longestStreak.toString(),
		stringResource(R.string.stats_label_hearts) to stats.hearts.toString(),
		stringResource(R.string.profile_stats_daily_goal) to stats.dailyGoal.toString(),
		stringResource(R.string.profile_stats_review_due) to dashboard.reviewDueCount.toString(),
		stringResource(R.string.profile_stats_lessons_completed) to dashboard.lessonsCompleted.toString(),
		stringResource(R.string.profile_stats_practice_sessions) to dashboard.practiceSessionsCompleted.toString(),
		stringResource(R.string.profile_stats_accuracy) to accuracy,
	)

	Column(Modifier.fillMaxWidth()) {
		Text(stringResource(R.string.profile_stats_title), style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(8.dp))
		tiles.chunked(2).forEach { row ->
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				row.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
				if (row.size == 1) Spacer(Modifier.weight(1f))
			}
			Spacer(Modifier.height(8.dp))
		}
	}
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
	Surface(
		color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
		shape = RoundedCornerShape(10.dp),
		modifier = modifier,
	) {
		Column(Modifier.padding(12.dp)) {
			Text(value, style = MaterialTheme.typography.titleLarge)
			Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
		}
	}
}

@Composable
private fun MasteryBars(entries: List<WordMasteryEntry>) {
	Column(Modifier.fillMaxWidth()) {
		Text(stringResource(R.string.profile_mastery_title), style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(8.dp))
		entries.forEach { entry ->
			val ratio = if (entry.total > 0) entry.mastered.toFloat() / entry.total else 0f
			val fraction = if (ratio <= 0f) 0f else ratio.coerceAtLeast(MIN_VISIBLE_FRACTION).coerceAtMost(1f)
			// TalkBack parity with the web bars (role="progressbar" + aria-label/valuenow).
			val barDescription = stringResource(R.string.profile_mastery_level, entry.cefrLevel)
			Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
				Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
					Text(entry.cefrLevel, style = MaterialTheme.typography.labelLarge)
					Text(
						stringResource(R.string.profile_mastery_count, entry.mastered, entry.total),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.secondary,
					)
				}
				Spacer(Modifier.height(4.dp))
				Box(
					Modifier
						.fillMaxWidth()
						.height(10.dp)
						.clip(RoundedCornerShape(6.dp))
						.background(MaterialTheme.colorScheme.surfaceVariant)
						.semantics {
							contentDescription = barDescription
							progressBarRangeInfo = ProgressBarRangeInfo(
								current = entry.mastered.toFloat(),
								range = 0f..entry.total.toFloat().coerceAtLeast(1f),
							)
						},
				) {
					Box(
						Modifier
							.fillMaxWidth(fraction)
							.fillMaxHeight()
							.clip(RoundedCornerShape(6.dp))
							.background(MaterialTheme.colorScheme.primary),
					)
				}
			}
		}
	}
}

/**
 * Registered-learner account actions (E13/US-13.4): export + delete, delete gated by an
 * inline confirm step (never a system dialog dismissible by accident).
 */
@Composable
private fun AccountActions(ui: ProfileUiState, viewModel: ProfileViewModel) {
	Column(Modifier.fillMaxWidth()) {
		Text(stringResource(R.string.profile_actions_title), style = MaterialTheme.typography.titleMedium)
		Spacer(Modifier.height(8.dp))

		if (ui.exportError) {
			Text(
				stringResource(R.string.profile_actions_export_error),
				color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.bodySmall,
			)
			Spacer(Modifier.height(4.dp))
		}
		OutlinedButton(
			onClick = viewModel::startExport,
			enabled = ui.editsEnabled && !ui.exporting,
			modifier = Modifier.fillMaxWidth(),
		) { Text(stringResource(R.string.profile_actions_export)) }

		Spacer(Modifier.height(8.dp))

		if (ui.deleteError) {
			Text(
				stringResource(R.string.profile_actions_delete_error),
				color = MaterialTheme.colorScheme.error,
				style = MaterialTheme.typography.bodySmall,
			)
			Spacer(Modifier.height(4.dp))
		}
		if (!ui.confirmingDelete) {
			OutlinedButton(
				onClick = viewModel::startDeleteConfirm,
				enabled = ui.editsEnabled,
				colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
				modifier = Modifier.fillMaxWidth(),
			) { Text(stringResource(R.string.profile_actions_delete)) }
		} else {
			Surface(
				color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
				shape = RoundedCornerShape(12.dp),
				modifier = Modifier.fillMaxWidth(),
			) {
				Column(Modifier.padding(12.dp)) {
					Text(stringResource(R.string.profile_actions_delete_confirm_prompt), style = MaterialTheme.typography.bodyMedium)
					Spacer(Modifier.height(8.dp))
					Row {
						Button(onClick = viewModel::deleteAccount, enabled = !ui.deleting) {
							Text(stringResource(R.string.profile_actions_delete_confirm))
						}
						Spacer(Modifier.width(8.dp))
						TextButton(onClick = viewModel::cancelDelete, enabled = !ui.deleting) {
							Text(stringResource(R.string.profile_cancel))
						}
					}
				}
			}
		}
	}
}

/** Shown when the dashboard on screen came from the Room cache, not the network (NFR-AN4). */
@Composable
private fun OfflineCopyBanner() {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.padding(bottom = 8.dp)
			.background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
			.padding(horizontal = 10.dp, vertical = 6.dp),
	) {
		Text(
			stringResource(R.string.profile_offline_copy),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
	}
}
