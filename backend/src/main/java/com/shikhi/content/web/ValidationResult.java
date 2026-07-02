package com.shikhi.content.web;

import java.util.List;

/** Outcome of validating a draft version (contract {@code ValidationResult}). */
public record ValidationResult(boolean valid, List<Issue> issues) {

	public record Issue(String path, String code, String message) {
	}
}
