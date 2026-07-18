package com.shikhi.app.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Point-in-time connectivity check (docs/93-offline-learning-design.md §3.3): a lesson/practice
 * session resolves its play source **once, at session start**, by calling [isOnline] a single
 * time — it is deliberately not observed/polled as a [kotlinx.coroutines.flow.Flow] mid-session,
 * so a session's grading source never switches mid-play even if connectivity changes while a
 * learner is answering.
 *
 * Checks for a *validated* path to the internet on the active network
 * (`NET_CAPABILITY_INTERNET` + `NET_CAPABILITY_VALIDATED`), not just "some network interface is
 * up" (e.g. a captive Wi-Fi portal with no real internet access reports as offline here).
 */
@Singleton
class ConnectivityChecker @Inject constructor(@param:ApplicationContext private val context: Context) {

	fun isOnline(): Boolean {
		val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
			?: return false
		val network = manager.activeNetwork ?: return false
		val capabilities = manager.getNetworkCapabilities(network) ?: return false
		return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
			capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
	}
}
