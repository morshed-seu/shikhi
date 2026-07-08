package com.shikhi.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shikhi.app.R
import com.shikhi.app.data.api.dto.CEFR_LEVELS

@Composable
private fun bandName(band: String): String = stringResource(
	when (band) {
		"A1" -> R.string.practice_band_a1
		"A2" -> R.string.practice_band_a2
		"B1" -> R.string.practice_band_b1
		"B2" -> R.string.practice_band_b2
		else -> R.string.practice_band_c1
	},
)

/**
 * One selectable CEFR level: the band code worn as a badge (echoing the hero's own),
 * its plain-language name, and a check on the current level. Full-width so nothing wraps.
 */
@Composable
private fun LevelOption(
	band: String,
	name: String,
	active: Boolean,
	enabled: Boolean,
	onClick: () -> Unit,
) {
	Surface(
		onClick = onClick,
		enabled = enabled,
		shape = RoundedCornerShape(12.dp),
		color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
		border = BorderStroke(
			1.dp,
			if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
		),
		modifier = Modifier.fillMaxWidth(),
	) {
		Row(
			Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Box(
				Modifier
					.size(width = 40.dp, height = 28.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(
						if (active) MaterialTheme.colorScheme.primary
						else MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
					),
				contentAlignment = Alignment.Center,
			) {
				Text(
					band,
					style = MaterialTheme.typography.labelLarge,
					color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
				)
			}
			Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
			if (active) {
				Text("✓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
			}
		}
	}
}

/**
 * The signed-in home hero (web PracticeHero, E12/US-12.2): one clear "start session"
 * action with the learner's CEFR band worn as a badge; tapping it opens self-placement.
 */
@Composable
fun PracticeHero(
	level: String,
	streak: Int,
	saving: Boolean,
	error: Boolean,
	onPickLevel: (String) -> Unit,
	onStart: () -> Unit,
) {
	var picking by remember { mutableStateOf(false) }

	Surface(
		color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
		shape = RoundedCornerShape(16.dp),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(Modifier.padding(16.dp)) {
			Text(
				stringResource(R.string.practice_hero_eyebrow),
				style = MaterialTheme.typography.labelMedium,
				color = MaterialTheme.colorScheme.secondary,
			)
			Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
				Text(stringResource(R.string.practice_hero_title), style = MaterialTheme.typography.titleLarge)
				Spacer(Modifier.weight(1f))
				OutlinedButton(
					onClick = { picking = !picking },
					contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
				) { Text("$level ▾") }
			}
			Spacer(Modifier.height(4.dp))
			Text(stringResource(R.string.practice_hero_copy, level), style = MaterialTheme.typography.bodyMedium)

			if (error) {
				Spacer(Modifier.height(8.dp))
				Text(
					stringResource(R.string.practice_level_error),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.error,
				)
			}

			if (picking) {
				Spacer(Modifier.height(12.dp))
				Text(stringResource(R.string.practice_pick_level), style = MaterialTheme.typography.labelLarge)
				Spacer(Modifier.height(8.dp))
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					CEFR_LEVELS.forEach { band ->
						LevelOption(
							band = band,
							name = bandName(band),
							active = band == level,
							enabled = !saving,
							onClick = {
								onPickLevel(band)
								picking = false
							},
						)
					}
				}
			}

			Spacer(Modifier.height(12.dp))
			Row(verticalAlignment = Alignment.CenterVertically) {
				Button(onClick = onStart) { Text(stringResource(R.string.practice_start)) }
				if (streak > 0) {
					Spacer(Modifier.width(12.dp))
					Text("🔥 $streak")
				}
			}
		}
	}
}
