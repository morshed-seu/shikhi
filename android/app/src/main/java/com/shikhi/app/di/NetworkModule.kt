package com.shikhi.app.di

import com.shikhi.app.BuildConfig
import com.shikhi.app.data.api.AuthApi
import com.shikhi.app.data.api.ContentApi
import com.shikhi.app.data.api.LearningApi
import com.shikhi.app.data.api.ProgressApi
import com.shikhi.app.data.api.UserApi
import com.shikhi.app.data.auth.AuthInterceptor
import com.shikhi.app.data.auth.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier annotation class PlainClient

@Qualifier annotation class AuthedClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

	private val json = Json {
		ignoreUnknownKeys = true
		explicitNulls = false
	}

	@Provides
	@Singleton
	@PlainClient
	fun plainOkHttp(): OkHttpClient = OkHttpClient.Builder()
		.connectTimeout(10, TimeUnit.SECONDS)
		.readTimeout(20, TimeUnit.SECONDS)
		.build()

	@Provides
	@Singleton
	@AuthedClient
	fun authedOkHttp(
		@PlainClient base: OkHttpClient,
		interceptor: AuthInterceptor,
		authenticator: TokenAuthenticator,
	): OkHttpClient = base.newBuilder()
		.addInterceptor(interceptor)
		.authenticator(authenticator)
		.build()

	private fun retrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
		.baseUrl(BuildConfig.API_BASE_URL)
		.client(client)
		.addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
		.build()

	@Provides
	@Singleton
	fun authApi(@PlainClient client: OkHttpClient): AuthApi =
		retrofit(client).create(AuthApi::class.java)

	@Provides
	@Singleton
	fun userApi(@AuthedClient client: OkHttpClient): UserApi =
		retrofit(client).create(UserApi::class.java)

	@Provides
	@Singleton
	fun contentApi(@AuthedClient client: OkHttpClient): ContentApi =
		retrofit(client).create(ContentApi::class.java)

	@Provides
	@Singleton
	fun learningApi(@AuthedClient client: OkHttpClient): LearningApi =
		retrofit(client).create(LearningApi::class.java)

	@Provides
	@Singleton
	fun progressApi(@AuthedClient client: OkHttpClient): ProgressApi =
		retrofit(client).create(ProgressApi::class.java)
}
