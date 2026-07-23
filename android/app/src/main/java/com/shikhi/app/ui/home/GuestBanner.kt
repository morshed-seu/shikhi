package com.shikhi.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.shikhi.app.R
import com.shikhi.app.data.api.ApiErrorCodes
import com.shikhi.app.data.api.apiError
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/** GF3: the banner offers two paths — CLAIM (upgrade this guest in place) or SIGN_IN
 * (activate a different, pre-existing account, discarding this device's guest progress). */
enum class GuestBannerMode { CLAIM, SIGN_IN }

data class GuestBannerUiState(
	val open: Boolean = false,
	val mode: GuestBannerMode = GuestBannerMode.CLAIM,
	val email: String = "",
	val password: String = "",
	val displayName: String = "",
	val submitting: Boolean = false,
	/** Which error to show: the special email-taken copy, or the generic one. */
	val emailTaken: Boolean = false,
	val error: String? = null,
	/** GF4: set when the failure was a client-side IOException (no HTTP response at all —
	 * e.g. a cold-start timeout) rather than a real HTTP error, so the UI can show a
	 * "server is waking up" hint instead of a bare/blank generic error. */
	val serverWarming: Boolean = false,
	/** GF3: sign-in is lossy (ADR-0011, no cross-account merge) — require explicit confirmation. */
	val confirmingDiscard: Boolean = false,
	/** OG4 (docs/94 §3.4): true while [AuthRepository.session] is [SessionState.LocalGuest] —
	 * there is no server account yet to claim or discard, so both actions are disabled (not
	 * hidden, so the layout doesn't shift once [GuestRegistrationWorker] completes). */
	val localGuest: Boolean = false,
)

@HiltViewModel
class GuestBannerViewModel @Inject constructor(
	private val authRepository: AuthRepository,
) : ViewModel() {

	private val _state = MutableStateFlow(GuestBannerUiState())
	val state: StateFlow<GuestBannerUiState> = _state

	init {
		// OG4: a LocalGuest has no server account yet — nothing to claim or discard. Track the
		// session so both actions can be disabled until GuestRegistrationWorker flips to Active,
		// and so an open form collapses back rather than submitting into a void.
		viewModelScope.launch {
			authRepository.session.collect { session ->
				val local = session is SessionState.LocalGuest
				_state.update { if (local) it.copy(localGuest = true, open = false, confirmingDiscard = false) else it.copy(localGuest = false) }
			}
		}
	}

	fun open() = _state.update {
		if (it.localGuest) it
		else it.copy(open = true, mode = GuestBannerMode.CLAIM, error = null, emailTaken = false, serverWarming = false, confirmingDiscard = false)
	}

	/** GF3: switch the (already-open, or not-yet-open) form to the sign-in path. */
	fun selectSignIn() = _state.update {
		if (it.localGuest) it
		else it.copy(open = true, mode = GuestBannerMode.SIGN_IN, error = null, emailTaken = false, serverWarming = false, confirmingDiscard = false)
	}

	fun setEmail(v: String) = _state.update { it.copy(email = v) }

	fun setPassword(v: String) = _state.update { it.copy(password = v) }

	fun setDisplayName(v: String) = _state.update { it.copy(displayName = v) }

	/** GF3: back out of the "this will discard your guest progress" confirmation. */
	fun cancelDiscardConfirm() = _state.update { it.copy(confirmingDiscard = false) }

	fun submit() {
		val s = _state.value
		if (s.localGuest || s.submitting || s.email.isBlank() || s.password.length < 8) return
		when (s.mode) {
			GuestBannerMode.CLAIM -> {
				_state.update { it.copy(submitting = true, emailTaken = false, serverWarming = false, error = null) }
				viewModelScope.launch {
					try {
						authRepository.claim(s.email, s.password, s.displayName.ifBlank { null })
						// Success flips the session user to non-guest; the banner unmounts.
					} catch (e: Exception) {
						val taken = e is HttpException && e.apiError()?.code == ApiErrorCodes.EMAIL_ALREADY_REGISTERED
						_state.update {
							it.copy(
								submitting = false,
								emailTaken = taken,
								serverWarming = e.isColdStartTimeout(),
								error = if (taken) null else apiMessageFor(e),
							)
						}
					}
				}
			}
			GuestBannerMode.SIGN_IN -> {
				// GF3: signing into a different account is lossy for this device's guest
				// progress (ADR-0011: no cross-account merge) — gate on explicit confirmation
				// instead of calling login() straight away.
				if (!s.confirmingDiscard) {
					_state.update { it.copy(confirmingDiscard = true) }
				}
			}
		}
	}

	/** GF3: the user confirmed the discard warning — actually activate the other account. */
	fun confirmDiscardAndSignIn() {
		val s = _state.value
		if (s.localGuest) return
		_state.update { it.copy(submitting = true, confirmingDiscard = false, serverWarming = false, error = null) }
		viewModelScope.launch {
			try {
				authRepository.login(s.email, s.password)
				// Success flips the session to the other account; the banner unmounts.
			} catch (e: Exception) {
				_state.update {
					it.copy(submitting = false, serverWarming = e.isColdStartTimeout(), error = apiMessageFor(e))
				}
			}
		}
	}
}

/** GF4: a client-side IOException (e.g. SocketTimeoutException) means no HTTP response
 * ever arrived — most likely the free-tier backend cold-starting — rather than a real
 * HTTP error. Distinguish it so the UI can hint "try again shortly" instead of a bare
 * generic error. */
private fun Exception.isColdStartTimeout(): Boolean = this is IOException

/** The backend's parseable error message, or null if there isn't one (non-JSON/empty
 * body, or the failure wasn't an HttpException at all — including cold-start timeouts,
 * which are handled separately via [Exception.isColdStartTimeout]). */
private fun apiMessageFor(e: Exception): String? =
	if (e.isColdStartTimeout()) null else (e as? HttpException)?.apiError()?.message

/**
 * Conversion prompt shown only to guests (web GuestBanner): expands into a claim form
 * that upgrades the account in place. Email-already-registered explains that we can't
 * merge — log in instead.
 */
@Composable
fun GuestBanner(viewModel: GuestBannerViewModel = hiltViewModel()) {
	val s by viewModel.state.collectAsStateWithLifecycle()

	Surface(
		color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
		shape = RoundedCornerShape(12.dp),
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(Modifier.padding(16.dp)) {
			Text(stringResource(R.string.guest_lead), style = MaterialTheme.typography.bodyMedium)
			Spacer(Modifier.height(12.dp))
			if (!s.open) {
				OutlinedButton(onClick = viewModel::open, enabled = !s.localGuest) {
					Text(stringResource(R.string.guest_save))
				}
				Spacer(Modifier.height(8.dp))
				TextButton(onClick = viewModel::selectSignIn, enabled = !s.localGuest) {
					Text(stringResource(R.string.guest_signin_cta))
				}
				if (s.localGuest) {
					// OG4 (docs/94 §3.4): disabled, not hidden — the layout must not shift when
					// GuestRegistrationWorker finishes and the actions become live.
					Spacer(Modifier.height(8.dp))
					Text(
						stringResource(R.string.guest_local_pending),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						style = MaterialTheme.typography.bodySmall,
					)
				}
			} else {
				if (s.mode == GuestBannerMode.CLAIM) {
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
				OutlinedTextField(
					value = s.password,
					onValueChange = viewModel::setPassword,
					label = { Text(stringResource(R.string.auth_password)) },
					visualTransformation = PasswordVisualTransformation(),
					modifier = Modifier.fillMaxWidth(),
				)
				if (s.emailTaken) {
					Spacer(Modifier.height(8.dp))
					Text(
						stringResource(R.string.guest_email_taken),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall,
					)
				} else if (s.serverWarming) {
					// GF4: cold-start timeout (no HTTP response at all) — friendlier than a bare error.
					Spacer(Modifier.height(8.dp))
					Text(
						stringResource(R.string.error_server_warming),
						color = MaterialTheme.colorScheme.error,
						style = MaterialTheme.typography.bodySmall,
					)
				} else if (s.error != null) {
					Spacer(Modifier.height(8.dp))
					Text(s.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
				}
				Spacer(Modifier.height(12.dp))
				Button(onClick = viewModel::submit, enabled = !s.submitting, modifier = Modifier.fillMaxWidth()) {
					Text(
						stringResource(
							when {
								s.submitting -> R.string.auth_submitting
								s.mode == GuestBannerMode.CLAIM -> R.string.guest_create_account
								else -> R.string.guest_signin_submit
							}
						)
					)
				}
			}
		}
	}

	// GF3: sign-in into a different account discards this device's guest progress
	// (ADR-0011 — no cross-account merge). Require an explicit, irreversible-aware confirmation.
	if (s.confirmingDiscard) {
		AlertDialog(
			onDismissRequest = viewModel::cancelDiscardConfirm,
			title = { Text(stringResource(R.string.auth_signin_discard_title)) },
			text = { Text(stringResource(R.string.auth_signin_discard_warning)) },
			confirmButton = {
				TextButton(onClick = viewModel::confirmDiscardAndSignIn) {
					Text(stringResource(R.string.guest_signin_submit))
				}
			},
			dismissButton = {
				TextButton(onClick = viewModel::cancelDiscardConfirm) {
					Text(stringResource(R.string.profile_cancel))
				}
			},
		)
	}
}
