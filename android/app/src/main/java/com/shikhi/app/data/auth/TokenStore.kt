package com.shikhi.app.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Token custody, mirroring the web client (frontend/src/auth/AuthProvider.tsx):
 * the access token lives in memory only; the rotating refresh token is persisted.
 *
 * `setSession` must complete its write BEFORE any retry that uses the new pair is sent —
 * the backend revokes the whole refresh-token family if a rotated token is replayed
 * (RefreshTokenService), so losing a rotation means a silent logout.
 */
interface TokenStore {
	val accessToken: StateFlow<String?>
	suspend fun currentRefreshToken(): String?
	suspend fun setSession(accessToken: String, refreshToken: String)
	suspend fun clear()
}

@Singleton
class DataStoreTokenStore @Inject constructor(
	private val dataStore: DataStore<Preferences>,
	private val cipher: RefreshTokenCipher,
) : TokenStore {

	private val key = stringPreferencesKey("refresh_token")

	private val _accessToken = MutableStateFlow<String?>(null)
	override val accessToken: StateFlow<String?> = _accessToken

	override suspend fun currentRefreshToken(): String? {
		val sealed = dataStore.data.first()[key] ?: return null
		// An undecryptable token (key rotated/wiped by the OS) is the same as no token.
		return runCatching { cipher.decrypt(sealed) }.getOrNull()
	}

	override suspend fun setSession(accessToken: String, refreshToken: String) {
		dataStore.edit { it[key] = cipher.encrypt(refreshToken) }
		_accessToken.value = accessToken
	}

	override suspend fun clear() {
		dataStore.edit { it.remove(key) }
		_accessToken.value = null
	}
}
