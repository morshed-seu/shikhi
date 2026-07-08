package com.shikhi.app.data.api

import com.shikhi.app.data.api.dto.ApiError
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** Well-known backend error codes the UI branches on (contract `Error.code`). */
object ApiErrorCodes {
	const val EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED"
}

private val lenientJson = Json { ignoreUnknownKeys = true }

/**
 * The structured error body of a failed call, if the backend sent one (contract `Error`:
 * code + localized message). Null for non-JSON bodies (proxies, hard 5xxs).
 */
fun HttpException.apiError(): ApiError? = try {
	response()?.errorBody()?.string()?.let { lenientJson.decodeFromString<ApiError>(it) }
} catch (e: Exception) {
	null
}
