package com.shikhi.platform.error;

import org.springframework.http.HttpStatus;

/**
 * An application error that maps to a specific HTTP status and a stable, machine-readable
 * {@code code} (per the API contract {@code Error} schema). Messages are human-readable;
 * full BN/EN localization of messages is a later milestone.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	public static ApiException unauthorized(String message) {
		return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
	}

	public static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
	}
}
