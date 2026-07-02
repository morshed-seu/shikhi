package com.shikhi.learning.grading;

import java.util.Locale;

/**
 * Normalization pipeline for free-text grading (LLD §5): trim → collapse internal
 * whitespace → case-fold → strip trailing punctuation. Curated answer variants live in the
 * content (multiple accepted answers), so this stays intentionally simple and predictable.
 */
public final class AnswerNormalizer {

	private AnswerNormalizer() {
	}

	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String collapsed = raw.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
		// Strip trailing sentence punctuation (., !, ?) — English/Bengali danda handled generally.
		return collapsed.replaceAll("[.!?।]+$", "").trim();
	}
}
