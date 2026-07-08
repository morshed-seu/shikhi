package com.shikhi.app.data.api.dto

import kotlinx.serialization.Serializable

// Wire shapes mirror docs/43-api-contract.openapi.yaml (TokenPair, User, Error, *Request),
// the same contract the web SPA consumes (frontend/src/api/auth.ts).

@Serializable
data class TokenPair(
	val accessToken: String,
	val refreshToken: String,
	val expiresIn: Long,
)

@Serializable
data class User(
	val id: String,
	val displayName: String? = null,
	val uiLocale: String = "bn",
	val roles: List<String> = emptyList(),
	val isGuest: Boolean = false,
)

@Serializable
data class ApiError(
	val code: String,
	val message: String,
	val correlationId: String? = null,
)

@Serializable
data class GuestRequest(val uiLocale: String? = null)

@Serializable
data class RefreshRequest(val refreshToken: String)
