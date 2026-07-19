package com.shikhi.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
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
import com.shikhi.app.ui.onboarding.ConnectingScreen
import com.shikhi.app.ui.theme.ShikhiTheme
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (not ComponentActivity) is load-bearing for the E13 locale switch:
// AppCompatDelegate.setApplicationLocales only STORES the choice on API < 33 — it is
// APPLIED via AppCompatActivity.attachBaseContext. On the PRD's reference devices
// (Android 9–13, mostly pre-33) a ComponentActivity would silently never switch language.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

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

						// GF1: guest provisioning is instantaneous/transient in practice — LoggedOut
						// is folded in here as a belt-and-suspenders case, but the user-visible
						// terminal failure state is GuestUnavailable (offline / backend down).
						is SessionState.LoggedOut,
						is SessionState.GuestUnavailable,
						-> ConnectingScreen(onRetry = viewModel::retryGuestProvisioning)

						// Offline/cold-start with a stored session: enter the app so cached
						// content renders (NFR-AN4); APIs self-heal via the Authenticator.
						// OG1: LocalGuest renders identically to Active — no learner-visible
						// difference while GuestRegistrationWorker registers in the background.
						is SessionState.Unavailable,
						is SessionState.LocalGuest,
						is SessionState.Active,
						-> ShikhiNavHost()
					}
				}
			}
		}
	}
}
