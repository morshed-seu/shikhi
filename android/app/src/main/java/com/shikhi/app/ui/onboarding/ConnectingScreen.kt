package com.shikhi.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shikhi.app.R

/**
 * GF1: replaces the old blocking login/signup wall. Shown only while/if silently
 * auto-provisioning a guest session (see AuthRepository.provisionGuestOrFail) hasn't
 * resolved yet — normally invisible, since guest creation is meant to happen silently
 * before the app ever reaches Home. [onRetry] re-runs that provisioning attempt.
 */
@Composable
fun ConnectingScreen(onRetry: () -> Unit) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(24.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		CircularProgressIndicator()
		Text(
			stringResource(R.string.connecting_message),
			style = MaterialTheme.typography.titleMedium,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(top = 16.dp),
		)
		Text(
			stringResource(R.string.connecting_retry_message),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			textAlign = TextAlign.Center,
			modifier = Modifier.padding(top = 8.dp),
		)
		Button(onClick = onRetry, modifier = Modifier.padding(top = 24.dp)) {
			Text(stringResource(R.string.retry))
		}
	}
}
