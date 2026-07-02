package com.shikhi.platform.web;

import org.slf4j.MDC;

/** Accessor for the per-request correlation id kept in the SLF4J MDC. */
public final class CorrelationId {

	public static final String MDC_KEY = "correlationId";
	public static final String HEADER = "X-Correlation-Id";

	private CorrelationId() {
	}

	/** Current request's correlation id, or {@code "unknown"} if none is bound. */
	public static String current() {
		String value = MDC.get(MDC_KEY);
		return value != null ? value : "unknown";
	}
}
