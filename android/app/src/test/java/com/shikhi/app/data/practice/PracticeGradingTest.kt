package com.shikhi.app.data.practice

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verbatim-behavior checks against the backend's `PracticeSessionService.grade()`/`verdict()`
 * switch: MCQ types compare `selectedOptionId`, `SENTENCE_BUILD`/`TYPE_WORD` normalize + match
 * `accepted`.
 */
class PracticeGradingTest {

	private val mcqKey = buildJsonObject {
		put("correctOptionId", "opt-correct")
		put("revealText", "apple — আপেল")
	}

	private val textKey = buildJsonObject {
		putJsonArray("accepted") { add("apple"); add("an apple") }
		put("revealText", "apple — আপেল")
	}

	@Test
	fun `WORD_MEANING MEANING_WORD and SENTENCE_GAP grade by matching selectedOptionId`() {
		for (type in listOf(PracticeExerciseType.WORD_MEANING, PracticeExerciseType.MEANING_WORD, PracticeExerciseType.SENTENCE_GAP)) {
			assertTrue(type, PracticeGrading.grade(type, buildJsonObject { put("selectedOptionId", "opt-correct") }, mcqKey))
			assertFalse(type, PracticeGrading.grade(type, buildJsonObject { put("selectedOptionId", "opt-wrong") }, mcqKey))
		}
	}

	@Test
	fun `TYPE_WORD normalizes the submitted text before matching accepted answers`() {
		assertTrue(PracticeGrading.grade(PracticeExerciseType.TYPE_WORD, buildJsonObject { put("text", "  APPLE.  ") }, textKey))
		assertTrue(PracticeGrading.grade(PracticeExerciseType.TYPE_WORD, buildJsonObject { put("text", "an apple") }, textKey))
		assertFalse(PracticeGrading.grade(PracticeExerciseType.TYPE_WORD, buildJsonObject { put("text", "banana") }, textKey))
	}

	@Test
	fun `SENTENCE_BUILD joins tokenOrder and normalizes before matching accepted answers`() {
		val key = buildJsonObject {
			putJsonArray("accepted") { add("I eat an apple every day") }
			put("revealText", "I eat an apple every day.")
		}
		val correctOrder = buildJsonObject { putJsonArray("tokenOrder") { add("I"); add("eat"); add("an"); add("apple"); add("every"); add("day") } }
		assertTrue(PracticeGrading.grade(PracticeExerciseType.SENTENCE_BUILD, correctOrder, key))

		val wrongOrder = buildJsonObject { putJsonArray("tokenOrder") { add("apple"); add("I"); add("eat"); add("an"); add("every"); add("day") } }
		assertFalse(PracticeGrading.grade(PracticeExerciseType.SENTENCE_BUILD, wrongOrder, key))
	}

	@Test
	fun `SENTENCE_BUILD with a missing tokenOrder grades false instead of throwing`() {
		assertFalse(PracticeGrading.grade(PracticeExerciseType.SENTENCE_BUILD, buildJsonObject { }, textKey))
	}

	@Test
	fun `verdict reveals the answer key's revealText only on a wrong answer`() {
		val correct = PracticeGrading.verdict(true, mcqKey)
		assertTrue(correct.correct)
		assertEquals("Correct!", correct.feedback?.en)

		val wrong = PracticeGrading.verdict(false, mcqKey)
		assertFalse(wrong.correct)
		assertEquals("Correct answer: apple — আপেল", wrong.feedback?.en)
		assertEquals("সঠিক উত্তর: apple — আপেল", wrong.feedback?.bn)
	}
}
