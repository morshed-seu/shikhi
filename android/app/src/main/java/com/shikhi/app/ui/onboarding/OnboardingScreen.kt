package com.shikhi.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikhi.app.R

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
	val s by viewModel.state.collectAsStateWithLifecycle()

	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 32.dp, vertical = 48.dp),
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
		Spacer(Modifier.height(40.dp))

		// Guest-first (ADR-0011): the zero-friction path stays the hero action.
		Button(
			onClick = viewModel::startAsGuest,
			enabled = !s.busy,
			modifier = Modifier.fillMaxWidth(),
		) {
			Text(stringResource(if (s.busy) R.string.auth_submitting else R.string.continue_as_guest))
		}
		Spacer(Modifier.height(8.dp))
		Text(
			text = stringResource(R.string.onboarding_guest_hint),
			style = MaterialTheme.typography.bodySmall,
			textAlign = TextAlign.Center,
		)

		Spacer(Modifier.height(24.dp))
		Text(stringResource(R.string.auth_or), style = MaterialTheme.typography.labelMedium)
		Spacer(Modifier.height(8.dp))

		if (!s.formOpen) {
			TextButton(onClick = viewModel::toggleForm) {
				Text(stringResource(R.string.auth_login) + " / " + stringResource(R.string.auth_register))
			}
		} else {
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
				TextButton(onClick = { viewModel.setMode(AuthMode.LOGIN) }) {
					Text(
						stringResource(R.string.auth_login),
						color = if (s.mode == AuthMode.LOGIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
					)
				}
				TextButton(onClick = { viewModel.setMode(AuthMode.REGISTER) }) {
					Text(
						stringResource(R.string.auth_register),
						color = if (s.mode == AuthMode.REGISTER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
					)
				}
			}
			if (s.mode == AuthMode.REGISTER) {
				OutlinedTextField(
					value = s.displayName,
					onValueChange = viewModel::setDisplayName,
					label = { Text(stringResource(R.string.auth_display_name)) },
					modifier = Modifier.fillMaxWidth(),
				)
				Spacer(Modifier.height(8.dp))
			}
			OutlinedTextField(
				value = s.email,
				onValueChange = viewModel::setEmail,
				label = { Text(stringResource(R.string.auth_email)) },
				modifier = Modifier.fillMaxWidth(),
			)
			Spacer(Modifier.height(8.dp))
			var passwordVisible by remember { mutableStateOf(false) }
			OutlinedTextField(
				value = s.password,
				onValueChange = viewModel::setPassword,
				label = { Text(stringResource(R.string.auth_password)) },
				visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
				trailingIcon = {
					TextButton(onClick = { passwordVisible = !passwordVisible }) {
						Text(
							stringResource(
								if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
							),
							style = MaterialTheme.typography.labelSmall,
						)
					}
				},
				modifier = Modifier.fillMaxWidth(),
			)
			if (s.mode == AuthMode.LOGIN) {
				Row(
					modifier = Modifier.fillMaxWidth(),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Checkbox(
						checked = s.rememberMe,
						onCheckedChange = viewModel::setRememberMe,
					)
					Text(stringResource(R.string.auth_remember_me))
				}
			}
			Spacer(Modifier.height(12.dp))
			Button(
				onClick = viewModel::submitForm,
				enabled = !s.busy && s.email.isNotBlank() && s.password.isNotBlank(),
				modifier = Modifier.fillMaxWidth(),
			) {
				Text(
					stringResource(
						when {
							s.busy -> R.string.auth_submitting
							s.mode == AuthMode.LOGIN -> R.string.auth_login
							else -> R.string.auth_register
						},
					),
				)
			}
		}

		if (s.error) {
			Spacer(Modifier.height(16.dp))
			Text(
				text = s.errorMessage ?: stringResource(R.string.error_generic),
				color = MaterialTheme.colorScheme.error,
				textAlign = TextAlign.Center,
			)
		}
	}
}
