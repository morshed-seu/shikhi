package com.shikhi.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.work.WorkManager
import com.shikhi.app.data.auth.DataStoreLoginPrefs
import com.shikhi.app.data.auth.DataStoreTokenStore
import com.shikhi.app.data.auth.LoginPrefs
import com.shikhi.app.data.auth.TokenStore
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.ShikhiDatabase
import com.shikhi.app.data.prefs.SharedPrefsThemePrefs
import com.shikhi.app.data.prefs.ThemePrefs
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore("session")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

	@Provides
	@Singleton
	fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	@Provides
	@Singleton
	fun sessionDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
		context.sessionDataStore

	@Provides
	@Singleton
	fun database(@ApplicationContext context: Context): ShikhiDatabase =
		Room.databaseBuilder(context, ShikhiDatabase::class.java, "shikhi.db")
			// Pre-release schema changes drop local caches/outbox; fine before first ship.
			.fallbackToDestructiveMigration(dropAllTables = true)
			.build()

	@Provides
	fun outboxDao(db: ShikhiDatabase): OutboxDao = db.outboxDao()

	@Provides
	fun contentCacheDao(db: ShikhiDatabase): ContentCacheDao = db.contentCacheDao()

	@Provides
	@Singleton
	fun workManager(@ApplicationContext context: Context): WorkManager =
		WorkManager.getInstance(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TokenStoreModule {

	@Binds
	abstract fun tokenStore(impl: DataStoreTokenStore): TokenStore

	@Binds
	abstract fun loginPrefs(impl: DataStoreLoginPrefs): LoginPrefs

	@Binds
	abstract fun themePrefs(impl: SharedPrefsThemePrefs): ThemePrefs
}
