package com.shikhi.app.data.lesson.grading

import com.shikhi.app.data.content.db.LocalExerciseAnswer
import com.shikhi.app.data.content.db.LocalExerciseOption
import com.shikhi.app.data.content.db.LocalHint
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The actual behavioral spec for OF3: this must match
 * `backend/src/main/java/com/shikhi/learning/grading/RuleBasedGradingStrategy.java` exactly,
 * since the Kotlin port has no shared code with the Java original — a divergence here is a
 * silent behavior bug for every offline-graded lesson.
 */
class LessonGradingEngineTest {

	private val exerciseId = "ex-1"

	private fun mcqOptions(correctId: String = "opt-correct", wrongId: String = "opt-wrong") = listOf(
		LocalExerciseOption(id = correctId, exerciseId = exerciseId, textEn = "Hello", textBn = "হ্যালো", isCorrect = true, ordinal = 1),
		LocalExerciseOption(id = wrongId, exerciseId = exerciseId, textEn = "Goodbye", textBn = "বিদায়", isCorrect = false, ordinal = 2),
	)

	private fun answer(key: String, value: String) = buildJsonObject { put(key, value) }

	private fun tokenAnswer(vararg tokens: String) = buildJsonObject {
		putJsonArray("tokenOrder") { tokens.forEach { add(it) } }
	}

	// ---- MCQ ----

	@Test
	fun `MCQ correct option is graded correct with the standard feedback`() {
		val verdict = LessonGradingEngine.grade(
			"MCQ", answer("selectedOptionId", "opt-correct"), mcqOptions(), emptyList(), emptyList(),
		)
		assertTrue(verdict.correct)
		assertEquals("Correct!", verdict.feedback?.en)
		assertEquals("সঠিক!", verdict.feedback?.bn)
	}

	@Test
	fun `MCQ wrong option is graded incorrect`() {
		val verdict = LessonGradingEngine.grade(
			"MCQ", answer("selectedOptionId", "opt-wrong"), mcqOptions(), emptyList(), emptyList(),
		)
		assertFalse(verdict.correct)
	}

	@Test
	fun `LISTENING grades like MCQ`() {
		val verdict = LessonGradingEngine.grade(
			"LISTENING", answer("selectedOptionId", "opt-correct"), mcqOptions(), emptyList(), emptyList(),
		)
		assertTrue(verdict.correct)
	}

	@Test
	fun `MCQ with no matching option id at all is incorrect, not a crash`() {
		val verdict = LessonGradingEngine.grade(
			"MCQ", answer("selectedOptionId", "does-not-exist"), mcqOptions(), emptyList(), emptyList(),
		)
		assertFalse(verdict.correct)
	}

	// ---- TYPE_TRANSLATION / FILL_BLANK (text) ----

	@Test
	fun `text exercise matches an exact accepted answer`() {
		val accepted = listOf(LocalExerciseAnswer("a1", exerciseId, "I am fine", isPrimary = true))
		val verdict = LessonGradingEngine.grade("TYPE_TRANSLATION", answer("text", "I am fine"), emptyList(), accepted, emptyList())
		assertTrue(verdict.correct)
	}

	@Test
	fun `text exercise matches via normalization - case, whitespace, trailing punctuation`() {
		val accepted = listOf(LocalExerciseAnswer("a1", exerciseId, "I am fine", isPrimary = true))
		val verdict = LessonGradingEngine.grade(
			"TYPE_TRANSLATION", answer("text", "  I   AM  fine.  "), emptyList(), accepted, emptyList(),
		)
		assertTrue("normalization must fold case/whitespace/trailing punctuation before matching", verdict.correct)
	}

	@Test
	fun `text exercise matches any of several accepted variants`() {
		val accepted = listOf(
			LocalExerciseAnswer("a1", exerciseId, "I am fine", isPrimary = true),
			LocalExerciseAnswer("a2", exerciseId, "I'm fine", isPrimary = false),
		)
		val verdict = LessonGradingEngine.grade("FILL_BLANK", answer("text", "I'm fine"), emptyList(), accepted, emptyList())
		assertTrue(verdict.correct)
	}

	@Test
	fun `text exercise with no matching accepted answer is incorrect`() {
		val accepted = listOf(LocalExerciseAnswer("a1", exerciseId, "I am fine", isPrimary = true))
		val verdict = LessonGradingEngine.grade("FILL_BLANK", answer("text", "I are fine"), emptyList(), accepted, emptyList())
		assertFalse(verdict.correct)
	}

	// ---- WORD_BANK ----

	@Test
	fun `WORD_BANK correct token order joins with spaces and matches`() {
		val accepted = listOf(LocalExerciseAnswer("a1", exerciseId, "See you tomorrow", isPrimary = true))
		val verdict = LessonGradingEngine.grade("WORD_BANK", tokenAnswer("See", "you", "tomorrow"), emptyList(), accepted, emptyList())
		assertTrue(verdict.correct)
	}

	@Test
	fun `WORD_BANK wrong token order is incorrect`() {
		val accepted = listOf(LocalExerciseAnswer("a1", exerciseId, "See you tomorrow", isPrimary = true))
		val verdict = LessonGradingEngine.grade("WORD_BANK", tokenAnswer("tomorrow", "See", "you"), emptyList(), accepted, emptyList())
		assertFalse(verdict.correct)
	}

	@Test
	fun `WORD_BANK with a non-array tokenOrder is incorrect, not a crash`() {
		val accepted = listOf(LocalExerciseAnswer("a1", exerciseId, "See you tomorrow", isPrimary = true))
		val verdict = LessonGradingEngine.grade("WORD_BANK", answer("tokenOrder", "not-an-array"), emptyList(), accepted, emptyList())
		assertFalse(verdict.correct)
	}

	// ---- MATCH: unsupported ----

	@Test(expected = UnsupportedExerciseException::class)
	fun `MATCH throws UnsupportedExerciseException, matching the server`() {
		LessonGradingEngine.grade("MATCH", buildJsonObject { }, emptyList(), emptyList(), emptyList())
	}

	// ---- Wrong-answer hint precedence: WRONG_ANSWER -> PATTERN -> DEFAULT -> generic fallback ----

	@Test
	fun `wrong MCQ answer picks the exact WRONG_ANSWER hint over PATTERN and DEFAULT`() {
		val hints = listOf(
			LocalHint("h1", exerciseId, "DEFAULT", null, "Generic default", "সাধারণ ডিফল্ট"),
			LocalHint("h2", exerciseId, "PATTERN", "SOME_PATTERN", "Pattern tip", "প্যাটার্ন টিপ"),
			LocalHint("h3", exerciseId, "WRONG_ANSWER", "opt-wrong", "You picked Goodbye specifically", "আপনি নির্দিষ্টভাবে বিদায় বেছে নিয়েছেন"),
		)
		val verdict = LessonGradingEngine.grade("MCQ", answer("selectedOptionId", "opt-wrong"), mcqOptions(), emptyList(), hints)
		assertFalse(verdict.correct)
		assertEquals("You picked Goodbye specifically", verdict.feedback?.en)
		// WRONG_ANSWER hints don't report a pattern code (server parity).
		assertNull(verdict.matchedPatternCode)
	}

	@Test
	fun `wrong text answer picks WRONG_ANSWER hint via normalized trigger key match`() {
		val accepted = listOf(LocalExerciseAnswer("a1", exerciseId, "I am fine", isPrimary = true))
		val hints = listOf(
			LocalHint("h1", exerciseId, "WRONG_ANSWER", "I Fine", "Almost - needs the verb am", "প্রায় ঠিক"),
		)
		// triggerKey "I Fine" normalizes to "i fine", matching normalized submitted text.
		val verdict = LessonGradingEngine.grade("TYPE_TRANSLATION", answer("text", "I fine"), emptyList(), accepted, hints)
		assertFalse(verdict.correct)
		assertEquals("Almost - needs the verb am", verdict.feedback?.en)
	}

	@Test
	fun `wrong answer falls to PATTERN when no WRONG_ANSWER hint matches, and reports the pattern code`() {
		val hints = listOf(
			LocalHint("h1", exerciseId, "DEFAULT", null, "Generic default", "সাধারণ ডিফল্ট"),
			LocalHint("h2", exerciseId, "PATTERN", "COPULA", "Bengali drops to-be, English keeps it", "বাংলা তা বাদ দেয়"),
			LocalHint("h3", exerciseId, "WRONG_ANSWER", "some-other-key", "A different specific mistake", "একটি ভিন্ন ভুল"),
		)
		val verdict = LessonGradingEngine.grade("MCQ", answer("selectedOptionId", "opt-wrong"), mcqOptions(), emptyList(), hints)
		assertFalse(verdict.correct)
		assertEquals("Bengali drops to-be, English keeps it", verdict.feedback?.en)
		assertEquals("COPULA", verdict.matchedPatternCode)
	}

	@Test
	fun `wrong answer falls to DEFAULT when there is no WRONG_ANSWER or PATTERN hint`() {
		val hints = listOf(
			LocalHint("h1", exerciseId, "DEFAULT", null, "A greeting is how you say hi", "শুভেচ্ছা মানে"),
		)
		val verdict = LessonGradingEngine.grade("MCQ", answer("selectedOptionId", "opt-wrong"), mcqOptions(), emptyList(), hints)
		assertFalse(verdict.correct)
		assertEquals("A greeting is how you say hi", verdict.feedback?.en)
		assertNull(verdict.matchedPatternCode)
	}

	@Test
	fun `wrong answer with zero hints at all falls back to the hardcoded generic message`() {
		val verdict = LessonGradingEngine.grade("MCQ", answer("selectedOptionId", "opt-wrong"), mcqOptions(), emptyList(), emptyList())
		assertFalse(verdict.correct)
		assertEquals("Not quite — try again.", verdict.feedback?.en)
		assertEquals("ঠিক হয়নি — আবার চেষ্টা করুন।", verdict.feedback?.bn)
	}

	@Test
	fun `WORD_BANK wrong answer has no WRONG_ANSWER key concept and falls straight to PATTERN or DEFAULT`() {
		val accepted = listOf(LocalExerciseAnswer("a1", exerciseId, "See you tomorrow", isPrimary = true))
		val hints = listOf(
			// Even a WRONG_ANSWER hint present for a WORD_BANK exercise can never match, since
			// wrongAnswerKey() returns null for WORD_BANK (no keyed-mistake concept there).
			LocalHint("h1", exerciseId, "WRONG_ANSWER", "anything", "Should never match word bank", "কখনো মিলবে না"),
			LocalHint("h2", exerciseId, "DEFAULT", null, "Arrange the words in order", "ক্রমানুসারে সাজান"),
		)
		val verdict = LessonGradingEngine.grade(
			"WORD_BANK", tokenAnswer("tomorrow", "See", "you"), emptyList(), accepted, hints,
		)
		assertFalse(verdict.correct)
		assertEquals("Arrange the words in order", verdict.feedback?.en)
	}
}
