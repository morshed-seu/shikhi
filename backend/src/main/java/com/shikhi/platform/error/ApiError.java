package com.shikhi.platform.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Wire shape of an error response — matches the {@code Error} schema in the API contract. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String code, String message, Map<String, Object> details,
		String correlationId) {

	public static ApiError of(String code, String message, String correlationId) {
		return new ApiError(code, message, null, correlationId);
	}
}
