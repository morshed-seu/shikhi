package com.shikhi.app.data.content

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.dto.SetLevelRequest
import com.shikhi.app.data.api.dto.SyncBatchRequest
import com.shikhi.app.data.api.dto.User
import com.shikhi.app.data.auth.AuthRepository
import com.shikhi.app.data.auth.SessionState
import com.shikhi.app.data.content.db.ContentDatabase
import com.shikhi.app.data.content.seed.ContentSeedImporter
import com.shikhi.app.data.db.CachedPayload
import com.shikhi.app.data.db.ContentCacheDao
import com.shikhi.app.data.progress.StatsProjectionRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gate OF2 end-to-end smoke test: real bundled `assets/content-seed/` JSON -> real
 * [ContentSeedImporter] -> real (in-memory) [ContentDatabase] -> real [ContentReadDao] ->
 * [CachedContentRepository]. Exercises the whole zero-network read path this gate builds, not
 * just each piece against a fake, matching docs/93-offline-learning-design.md §3.2/§3.3 end to
 * end. The only fake here is the network-only `ProgressApi` (unused by the paths under test)
 * and `ContentCacheDao` (only touched by `stats()`, also not under test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineContentSmokeTest {

	private class FakeDataStore : DataStore<Preferences> {
		private val state = MutableStateFlow(emptyPreferences())
		override val data: Flow<Preferences> = state

		override suspend fun updateData(
			transform: suspend (t: Preferences) -> Preferences,
		): Preferences {
			val updated = transform(state.value)
			state.value = updated
			return updated
		}
	}

	private class UnusedProgressApi : ProgressApi {
		override suspend fun stats() = throw UnsupportedOperationException("not exercised by this smoke test")
		override suspend fun sync(body: SyncBatchRequest) = throw UnsupportedOperationException()
		override suspend fun setLevel(body: SetLevelRequest) = throw UnsupportedOperationException()
	}

	private class NoopContentCacheDao : ContentCacheDao {
		override suspend fun get(key: String): CachedPayload? = null
		override suspend fun put(payload: CachedPayload) = Unit
	}

	private lateinit var database: ContentDatabase

	@Before
	fun setUp() {
		val context = ApplicationProvider.getApplicationContext<Context>()
		database = Room.inMemoryDatabaseBuilder(context, ContentDatabase::class.java)
			.allowMainThreadQueries()
			.build()
	}

	@After
	fun tearDown() {
		database.close()
	}

	@Test
	fun `importing the bundled seed then reading through CachedContentRepository yields real content, zero network`() =
		runBlocking {
			val context = ApplicationProvider.getApplicationContext<Context>()
			val importer = ContentSeedImporter(context, database, FakeDataStore())

			val imported = importer.importIfNeeded()
			assertTrue("cold start: the seed hasn't imported yet, so this must run", imported)

			val authRepository = mockk<AuthRepository>()
			every { authRepository.session } returns MutableStateFlow(SessionState.Active(User(id = "user-1")))
			val repository = CachedContentRepository(
				progressApi = UnusedProgressApi(),
				cache = NoopContentCacheDao(),
				contentDao = database.contentReadDao(),
				statsProjectionRepository = mockk(relaxed = true),
				authRepository = authRepository,
				tokenStore = mockk(relaxed = true),
			)

			val vocab = repository.vocabulary("A1")
			assertTrue("A1 vocabulary must be readable with zero network calls", vocab != null && !vocab.fromCache)
			assertTrue(vocab!!.value.isNotEmpty())

			val curriculum = repository.curriculum()
			assertTrue("curriculum must be readable with zero network calls", curriculum != null && !curriculum.fromCache)
			assertEquals(13, curriculum!!.value.levels.sumOf { level -> level.units.sumOf { it.lessons.size } })
		}

	@Test
	fun `before the importer runs, both bundled reads report the not-yet-seeded contract`() = runBlocking {
		// No ContentSeedImporter call here — simulates the window before ContentSeedWorker
		// completes on a fresh install.
		val authRepository = mockk<AuthRepository>()
		every { authRepository.session } returns MutableStateFlow(SessionState.Active(User(id = "user-1")))
		val repository = CachedContentRepository(
			progressApi = UnusedProgressApi(),
			cache = NoopContentCacheDao(),
			contentDao = database.contentReadDao(),
			statsProjectionRepository = mockk(relaxed = true),
			authRepository = authRepository,
			tokenStore = mockk(relaxed = true),
		)

		assertEquals(null, repository.vocabulary("A1"))
		assertEquals(null, repository.curriculum())
	}
}
