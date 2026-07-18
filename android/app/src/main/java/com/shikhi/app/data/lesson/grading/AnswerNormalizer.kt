package com.shikhi.app.data.lesson.grading

import java.util.Locale

/**
 * Verbatim Kotlin port of the backend's `AnswerNormalizer`
 * (`backend/src/main/java/com/shikhi/learning/grading/AnswerNormalizer.java`, docs/
 * 93-offline-learning-design.md §4.3): trim → collapse internal whitespace → case-fold → strip
 * trailing sentence punctuation (`.`, `!`, `?`, the Bengali `।` danda). Curated answer variants
 * live in the content itself (multiple accepted answers per exercise), so this stays
 * intentionally simple and predictable — no stemming, no fuzzy matching.
 */
object AnswerNormalizer {

	private val WHITESPACE = Regex("\\s+")
	private val TRAILING_PUNCTUATION = Regex("[.!?।]+$")

	fun normalize(raw: String?): String {
		if (raw == null) return ""
		val collapsed = raw.trim().replace(WHITESPACE, " ").lowercase(Locale.ROOT)
		return collapsed.replace(TRAILING_PUNCTUATION, "").trim()
	}
}
