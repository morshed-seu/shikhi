package com.shikhi.app.data.outbox

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-foreground flush trigger — the Android analogue of the web client flushing the
 * outbox from App.tsx when the tab regains focus/connectivity. Registered once at
 * application start.
 */
@Singleton
class OutboxForegroundFlusher @Inject constructor(
	private val outbox: OutboxRepository,
	private val authRepository: AuthRepository,
	private val appScope: CoroutineScope,
) : DefaultLifecycleObserver {

	fun register() {
		ProcessLifecycleOwner.get().lifecycle.addObserver(this)
	}

	override fun onStart(owner: LifecycleOwner) {
		appScope.launch {
			if (authRepository.session.value is SessionState.Active) outbox.flush()
		}
	}
}
