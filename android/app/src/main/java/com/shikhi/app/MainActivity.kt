package com.shikhi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.ui.navigation.ShikhiNavHost
import com.shikhi.app.ui.onboarding.OnboardingScreen
import com.shikhi.app.ui.theme.ShikhiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

	private val viewModel: MainViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			ShikhiTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
					val session by viewModel.session.collectAsStateWithLifecycle()
					when (session) {
						is SessionState.Loading -> Box(
							modifier = Modifier.fillMaxSize(),
							contentAlignment = Alignment.Center,
						) { CircularProgressIndicator() }

						is SessionState.LoggedOut -> OnboardingScreen()

						// Offline/cold-start with a stored session: enter the app so cached
						// content renders (NFR-AN4); APIs self-heal via the Authenticator.
						is SessionState.Unavailable,
						is SessionState.Active,
						-> ShikhiNavHost()
					}
				}
			}
		}
	}
}
