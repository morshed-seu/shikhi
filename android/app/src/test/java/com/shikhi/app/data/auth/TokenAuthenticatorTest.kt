package com.shikhi.app.data.auth

import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.UserApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gate-1 correctness tests for the refresh-and-retry dance. The backend revokes the whole
 * refresh-token family when a rotated token is replayed, so these invariants are what keep
 * users from being silently logged out:
 *  1. the rotated refresh token is persisted before the retried request is sent,
 *  2. concurrent 401s produce exactly one /auth/refresh call,
 *  3. a 401 from /auth/refresh clears the session and does not retry.
 */
class TokenAuthenticatorTest {

	private class FakeTokenStore(access: String?, private var refresh: String?) : TokenStore {
		private val _accessToken = MutableStateFlow(access)
		override val accessToken: StateFlow<String?> = _accessToken
		val persistLog = mutableListOf<Pair<String, String>>()
		private var localGuestId: String? = null

		override suspend fun currentRefreshToken(): String? = refresh

		override suspend fun setSession(accessToken: String, refreshToken: String) {
			persistLog += accessToken to refreshToken
			refresh = refreshToken
			_accessToken.value = accessToken
		}

		override suspend fun clear() {
			refresh = null
			_accessToken.value = null
		}

		override suspend fun localGuestId(): String? = localGuestId

		override suspend fun setLocalGuestId(id: String) {
			localGuestId = id
		}

		override suspend fun clearLocalGuestId() {
			localGuestId = null
		}
	}

	private lateinit var server: MockWebServer
	private lateinit var store: FakeTokenStore

	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	@Before
	fun setUp() {
		server = MockWebServer()
		server.start()
		store = FakeTokenStore(access = "stale-access", refresh = "refresh-1")
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
		.baseUrl(server.url("/v1/"))
		.client(client)
		.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
		.build()

	private fun authedClient(): OkHttpClient {
		val plain = OkHttpClient()
		val authApi = retrofit(plain).create(AuthApi::class.java)
		val authenticator = TokenAuthenticator(store, dagger.Lazy { authApi })
		return plain.newBuilder()
			.addInterceptor(AuthInterceptor(store))
			.authenticator(authenticator)
			.build()
	}

	private fun tokenPairJson(n: Int) =
		"""{"accessToken":"access-$n","refreshToken":"refresh-$n","expiresIn":900}"""

	private val userJson =
		"""{"id":"u1","displayName":"Guest","uiLocale":"bn","roles":["LEARNER"],"isGuest":true}"""

	@Test
	fun `rotated refresh token is persisted before the retry is sent`() {
		val refreshCalls = AtomicInteger()
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse = when {
				request.path!!.endsWith("/auth/refresh") -> {
					refreshCalls.incrementAndGet()
					MockResponse().setBody(tokenPairJson(2)).addHeader("Content-Type", "application/json")
				}

				request.getHeader("Authorization") == "Bearer access-2" -> {
					// By the time the retry arrives, the rotation MUST already be on disk.
					assertEquals(listOf("access-2" to "refresh-2"), store.persistLog)
					MockResponse().setBody(userJson).addHeader("Content-Type", "application/json")
				}

				else -> MockResponse().setResponseCode(401)
			}
		}

		val userApi = retrofit(authedClient()).create(UserApi::class.java)
		val user = kotlinx.coroutines.runBlocking { userApi.me() }

		assertEquals("u1", user.id)
		assertEquals(1, refreshCalls.get())
		assertEquals(listOf("access-2" to "refresh-2"), store.persistLog)
	}

	@Test
	fun `concurrent 401s single-flight into one refresh call`() {
		val refreshCalls = AtomicInteger()
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse = when {
				request.path!!.endsWith("/auth/refresh") -> {
					refreshCalls.incrementAndGet()
					MockResponse().setBody(tokenPairJson(2)).addHeader("Content-Type", "application/json")
				}

				request.getHeader("Authorization") == "Bearer access-2" ->
					MockResponse().setBody(userJson).addHeader("Content-Type", "application/json")

				else -> MockResponse().setResponseCode(401)
			}
		}

		val userApi = retrofit(authedClient()).create(UserApi::class.java)
		val workers = 4
		val ready = CountDownLatch(workers)
		val done = CountDownLatch(workers)
		val failures = AtomicInteger()
		val pool = Executors.newFixedThreadPool(workers)
		repeat(workers) {
			pool.execute {
				ready.countDown()
				ready.await()
				try {
					kotlinx.coroutines.runBlocking { userApi.me() }
				} catch (e: Exception) {
					failures.incrementAndGet()
				} finally {
					done.countDown()
				}
			}
		}
		done.await()
		pool.shutdown()

		assertEquals(0, failures.get())
		assertEquals("exactly one refresh for $workers concurrent 401s", 1, refreshCalls.get())
	}

	@Test
	fun `refresh rejected with 401 clears the session and gives up`() {
		server.dispatcher = object : Dispatcher() {
			override fun dispatch(request: RecordedRequest): MockResponse = when {
				request.path!!.endsWith("/auth/refresh") -> MockResponse().setResponseCode(401)
				else -> MockResponse().setResponseCode(401)
			}
		}

		val userApi = retrofit(authedClient()).create(UserApi::class.java)
		val result = runCatching { kotlinx.coroutines.runBlocking { userApi.me() } }

		assertTrue(result.isFailure)
		assertNull(store.accessToken.value)
		assertNull(kotlinx.coroutines.runBlocking { store.currentRefreshToken() })
		assertTrue(store.persistLog.isEmpty())
	}

	@Test
	fun `requests without a bearer token are never retried`() {
		server.enqueue(MockResponse().setResponseCode(401))
		store = FakeTokenStore(access = null, refresh = "refresh-1")

		val userApi = retrofit(authedClient()).create(UserApi::class.java)
		val result = runCatching { kotlinx.coroutines.runBlocking { userApi.me() } }

		assertTrue(result.isFailure)
		assertEquals("no refresh attempt without a failed bearer", 1, server.requestCount)
	}
}
