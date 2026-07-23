package com.shikhi.app.data.lesson.grading

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the backend AnswerNormalizer's exact pipeline (LLD §5): trim → collapse whitespace →
 * lowercase → strip trailing sentence punctuation (`.`, `!`, `?`, the Bengali `।` danda). */
class AnswerNormalizerTest {

	@Test
	fun `trims leading and trailing whitespace`() {
		assertEquals("hello", AnswerNormalizer.normalize("  hello  "))
	}

	@Test
	fun `collapses internal whitespace runs to a single space`() {
		assertEquals("i am fine", AnswerNormalizer.normalize("I    am\tfine"))
	}

	@Test
	fun `lowercases`() {
		assertEquals("i am fine", AnswerNormalizer.normalize("I Am Fine"))
	}

	@Test
	fun `strips trailing sentence punctuation`() {
		assertEquals("i am fine", AnswerNormalizer.normalize("I am fine."))
		assertEquals("really", AnswerNormalizer.normalize("really!"))
		assertEquals("really", AnswerNormalizer.normalize("really?"))
		assertEquals("really", AnswerNormalizer.normalize("really!?"))
	}

	@Test
	fun `strips a trailing Bengali danda`() {
		assertEquals("আমি ভালো আছি", AnswerNormalizer.normalize("আমি ভালো আছি।"))
	}

	@Test
	fun `does not touch internal punctuation`() {
		assertEquals("i'm fine", AnswerNormalizer.normalize("I'm fine"))
	}

	@Test
	fun `null input normalizes to empty string`() {
		assertEquals("", AnswerNormalizer.normalize(null))
	}

	@Test
	fun `combines all rules together`() {
		assertEquals("i am fine", AnswerNormalizer.normalize("   I   AM   fine.  "))
	}
}
