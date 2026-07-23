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
import com.shikhi.app.data.content.db.ContentAnswerKeyDao
import com.shikhi.app.data.content.db.ContentDatabase
import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.db.LocalLessonCompletionDao
import com.shikhi.app.data.db.LocalPracticeSessionDao
import com.shikhi.app.data.db.LocalStatsProjectionDao
import com.shikhi.app.data.db.MIGRATION_2_3
import com.shikhi.app.data.db.MIGRATION_3_4
import com.shikhi.app.data.db.MIGRATION_4_5
import com.shikhi.app.data.db.OutboxDao
import com.shikhi.app.data.db.ShikhiDatabase
import com.shikhi.app.data.db.WordProgressDao
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
			// OF4 (docs/93-offline-learning-design.md §9 risk 2): this database now holds durable
			// per-user mastery/review state (LocalWordProgress/LocalReviewProgress), so the
			// blanket destructive fallback is retired for upgrades — a real Migration (v2->v3)
			// carries that state across the schema bump. Only a *downgrade* (an older APK
			// installed over a newer DB, e.g. a manual rollback) still falls back destructively,
			// since there is no meaningful way to migrate a schema backwards.
			.addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
			.fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
			.build()

	@Provides
	fun outboxDao(db: ShikhiDatabase): OutboxDao = db.outboxDao()

	@Provides
	fun contentCacheDao(db: ShikhiDatabase): ContentCacheDao = db.contentCacheDao()

	// OF4 §4.2: local practice/mastery state, injected only into the local practice path
	// (PracticeWordPicker / WordProgressEngine / LocalPracticeSource).
	@Provides
	fun wordProgressDao(db: ShikhiDatabase): WordProgressDao = db.wordProgressDao()

	@Provides
	fun localPracticeSessionDao(db: ShikhiDatabase): LocalPracticeSessionDao = db.localPracticeSessionDao()

	// UO2: durable per-user stats projection, injected into StatsProjectionRepository.
	@Provides
	fun localStatsProjectionDao(db: ShikhiDatabase): LocalStatsProjectionDao = db.localStatsProjectionDao()

	// UO4: local per-lesson first-completion ledger, injected into StatsProjectionRepository/LocalLessonSource.
	@Provides
	fun localLessonCompletionDao(db: ShikhiDatabase): LocalLessonCompletionDao = db.localLessonCompletionDao()

	// Bundled, read-only content DB (OF1 §3.2): separate Room database, reseeded wholesale
	// from ContentSeedImporter — never migrated row-by-row, so unlike `database()` above it
	// deliberately has no `.fallbackToDestructiveMigration()`; a missing-migration crash on a
	// future version bump is a later-gate concern (OF-later), not this one.
	@Provides
	@Singleton
	fun contentDatabase(@ApplicationContext context: Context): ContentDatabase =
		Room.databaseBuilder(context, ContentDatabase::class.java, "content.db").build()

	@Provides
	fun contentReadDao(db: ContentDatabase): ContentReadDao = db.contentReadDao()

	// Answer-key-only surface (OF3 §4.1): only injected into the local grading path
	// (LocalLessonSource / LessonGradingEngine), never into UI-facing code.
	@Provides
	fun contentAnswerKeyDao(db: ContentDatabase): ContentAnswerKeyDao = db.contentAnswerKeyDao()

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
}
