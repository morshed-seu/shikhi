package com.shikhi.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikhi.app.R

@androidx.compose.runtime.Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(horizontal = 32.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center,
	) {
		Text(
			text = stringResource(R.string.app_name),
			style = MaterialTheme.typography.displayLarge,
			color = MaterialTheme.colorScheme.primary,
		)
		Spacer(Modifier.height(12.dp))
		Text(
			text = stringResource(R.string.onboarding_tagline),
			style = MaterialTheme.typography.titleMedium,
			textAlign = TextAlign.Center,
		)
		Spacer(Modifier.height(48.dp))

		when (state) {
			OnboardingUiState.Busy -> CircularProgressIndicator()

			else -> Button(
				onClick = viewModel::startAsGuest,
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(stringResource(R.string.continue_as_guest))
			}
		}

		Spacer(Modifier.height(16.dp))
		Text(
			text = stringResource(R.string.onboarding_guest_hint),
			style = MaterialTheme.typography.bodySmall,
			textAlign = TextAlign.Center,
		)
		if (state == OnboardingUiState.Error) {
			Spacer(Modifier.height(16.dp))
			Text(
				text = stringResource(R.string.error_generic),
				color = MaterialTheme.colorScheme.error,
				textAlign = TextAlign.Center,
			)
		}
	}
}
