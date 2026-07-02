package com.shikhi.platform.web;

import com.shikhi.platform.error.ApiError;
import com.shikhi.platform.error.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates exceptions into the contract's {@code Error} shape, always with a correlation id. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> handleApi(ApiException ex) {
		return ResponseEntity.status(ex.getStatus())
				.body(ApiError.of(ex.getCode(), ex.getMessage(), CorrelationId.current()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, Object> fields = new LinkedHashMap<>();
		for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
			fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
		}
		ApiError body = new ApiError("VALIDATION_FAILED", "Request validation failed", fields,
				CorrelationId.current());
		return ResponseEntity.badRequest().body(body);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
		// Log the detail server-side; never leak internals to the client.
		log.error("Unhandled exception [correlationId={}]", CorrelationId.current(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiError.of("INTERNAL", "An unexpected error occurred",
						CorrelationId.current()));
	}
}
