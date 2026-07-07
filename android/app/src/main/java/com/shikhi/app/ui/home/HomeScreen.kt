package com.shikhi.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikhi.app.R

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
	val ui by viewModel.state.collectAsStateWithLifecycle()

	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(24.dp),
		verticalArrangement = Arrangement.Center,
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(
			text = stringResource(
				R.string.home_greeting,
				ui.displayName ?: stringResource(R.string.guest_display_name),
			),
			style = MaterialTheme.typography.headlineMedium,
		)
		Spacer(Modifier.height(24.dp))
		HealthBadge(ui.health)
		Spacer(Modifier.height(48.dp))
		TextButton(onClick = viewModel::logout) {
			Text(stringResource(R.string.log_out))
		}
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
	Row(verticalAlignment = Alignment.CenterVertically) {
		Box(
			modifier = Modifier
				.size(10.dp)
				.background(color, CircleShape),
		)
		Spacer(Modifier.width(8.dp))
		Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
	}
}
