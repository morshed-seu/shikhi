package com.shikhi.app.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** A remembered login prefilled into the form on a later visit. */
data class RememberedLogin(val email: String, val password: String?)

/**
 * Non-secret login conveniences, mirroring frontend/src/components/AuthPanel.tsx.
 * "Remember me" prefills the login form on a later visit. The email is stored in the clear;
 * the password — when the user opts in — is sealed with the same Android Keystore custody as
 * the refresh token ([[RefreshTokenCipher]]), never persisted as plaintext.
 */
interface LoginPrefs {
	suspend fun remembered(): RememberedLogin?
	suspend fun setRemembered(email: String?, password: String?)
}

@Singleton
class DataStoreLoginPrefs @Inject constructor(
	private val dataStore: DataStore<Preferences>,
	private val cipher: RefreshTokenCipher,
) : LoginPrefs {

	private val emailKey = stringPreferencesKey("remembered_email")
	private val passwordKey = stringPreferencesKey("remembered_password")

	override suspend fun remembered(): RememberedLogin? {
		val prefs = dataStore.data.first()
		val email = prefs[emailKey] ?: return null
		// An undecryptable password (key rotated/wiped by the OS) is the same as none.
		val password = prefs[passwordKey]?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
		return RememberedLogin(email, password)
	}

	override suspend fun setRemembered(email: String?, password: String?) {
		dataStore.edit { prefs ->
			if (email.isNullOrBlank()) prefs.remove(emailKey) else prefs[emailKey] = email
			if (password.isNullOrBlank()) prefs.remove(passwordKey)
			else prefs[passwordKey] = cipher.encrypt(password)
		}
	}
}
