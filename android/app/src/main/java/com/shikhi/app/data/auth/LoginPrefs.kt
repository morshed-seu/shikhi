package com.shikhi.app.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-secret login conveniences, mirroring frontend/src/components/AuthPanel.tsx.
 * Only the email is remembered so it can prefill the login form on a later visit — the
 * password is deliberately never stored (it belongs behind the same custody as tokens).
 */
interface LoginPrefs {
	suspend fun rememberedEmail(): String?
	suspend fun setRememberedEmail(email: String?)
}

@Singleton
class DataStoreLoginPrefs @Inject constructor(
	private val dataStore: DataStore<Preferences>,
) : LoginPrefs {

	private val key = stringPreferencesKey("remembered_email")

	override suspend fun rememberedEmail(): String? = dataStore.data.first()[key]

	override suspend fun setRememberedEmail(email: String?) {
		dataStore.edit { prefs ->
			if (email.isNullOrBlank()) prefs.remove(key) else prefs[key] = email
		}
	}
}
