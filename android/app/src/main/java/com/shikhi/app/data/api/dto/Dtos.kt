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
	/** Account creation instant (E13); absent for pre-E13 rows the backend hasn't backfilled. */
	val joinedAt: String? = null,
)

/** `PATCH /me` body — only display name and UI locale are editable (E13/US-13.1). */
@Serializable
data class UpdateProfileRequest(
	val displayName: String? = null,
	val uiLocale: String? = null,
)

/** A linked sign-in method (E13, `GET /me/identities`) — feeds the masked-email line. */
@Serializable
data class Identity(
	val provider: String = "EMAIL",
	val verified: Boolean = false,
	val maskedRef: String = "",
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
