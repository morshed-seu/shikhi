package com.shikhi.app.data.api

import com.shikhi.app.data.api.dto.ClaimRequest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * The claim flow branches on the backend's structured error body (contract `Error`):
 * EMAIL_ALREADY_REGISTERED gets the dedicated "log in instead" copy (ADR-0011 / web
 * GuestBanner). Pins that the parser survives both JSON and non-JSON error bodies.
 */
class ApiErrorsTest {

	private lateinit var server: MockWebServer
	private lateinit var api: UserApi

	@Before
	fun setUp() {
		server = MockWebServer()
		server.start()
		val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
		api = Retrofit.Builder()
			.baseUrl(server.url("/v1/"))
			.client(OkHttpClient())
			.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
			.build()
			.create(UserApi::class.java)
	}

	@After
	fun tearDown() {
		server.shutdown()
	}

	private fun claimError(): HttpException = runBlocking {
		try {
			api.claim(ClaimRequest("a@b.c", "password1"))
			throw AssertionError("expected HttpException")
		} catch (e: HttpException) {
			e
		}
	}

	@Test
	fun `structured 409 body parses into code and localized message`() {
		server.enqueue(
			MockResponse()
				.setResponseCode(409)
				.addHeader("Content-Type", "application/json")
				.setBody("""{"code":"EMAIL_ALREADY_REGISTERED","message":"এই ইমেইলে অ্যাকাউন্ট আছে","correlationId":"c-1"}"""),
		)

		val error = claimError().apiError()

		assertEquals(ApiErrorCodes.EMAIL_ALREADY_REGISTERED, error?.code)
		assertEquals("এই ইমেইলে অ্যাকাউন্ট আছে", error?.message)
	}

	@Test
	fun `non-JSON error body degrades to null instead of throwing`() {
		server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))

		assertNull(claimError().apiError())
	}
}
