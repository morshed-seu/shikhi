package com.shikhi.app.data.auth

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Attaches the in-memory access token to every request on the authenticated client. */
@Singleton
class AuthInterceptor @Inject constructor(
	private val tokenStore: TokenStore,
) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val token = tokenStore.accessToken.value ?: return chain.proceed(chain.request())
		val request = chain.request().newBuilder()
			.header("Authorization", "Bearer $token")
			.build()
		return chain.proceed(request)
	}
}
