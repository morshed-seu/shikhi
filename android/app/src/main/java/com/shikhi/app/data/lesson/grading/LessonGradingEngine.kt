package com.shikhi.app.data.lesson.grading

import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.Verdict
import com.shikhi.app.data.content.db.LocalExerciseAnswer
import com.shikhi.app.data.content.db.LocalExerciseOption
import com.shikhi.app.data.content.db.LocalHint
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Thrown for exercise types the grading engine cannot grade — mirrors the backend's
 * `ApiException.badRequest("UNSUPPORTED_EXERCISE", ...)` thrown by
 * `RuleBasedGradingStrategy.grade()` for `MATCH` (not in the pilot).
 */
class UnsupportedExerciseException(message: String) : Exception(message)

/**
 * Verbatim-behavior Kotlin port of the backend's `RuleBasedGradingStrategy`
 * (`backend/src/main/java/com/shikhi/learning/grading/RuleBasedGradingStrategy.java`,
 * docs/93-offline-learning-design.md §4.3). Grades `MCQ`/`TYPE_TRANSLATION`/`FILL_BLANK`/
 * `WORD_BANK`/`LISTENING` purely from the answer-key rows already loaded from
 * [com.shikhi.app.data.content.db.ContentAnswerKeyDao] — no I/O, no coroutines, fully
 * synchronous and unit-testable. `MATCH` stays unsupported, exactly matching the server.
 *
 * On a wrong answer, curated feedback is picked by the same precedence as the server:
 * `WRONG_ANSWER` (an exact, curated mistake) → `PATTERN` (an L1-transfer tip) → `DEFAULT`
 * (a generic per-exercise fallback) → a hardcoded generic message if the exercise has no hints
 * at all.
 */
object LessonGradingEngine {

	private val CORRECT_FEEDBACK = Bilingual("Correct!", "সঠিক!")
	private val GENERIC_WRONG = Bilingual("Not quite — try again.", "ঠিক হয়নি — আবার চেষ্টা করুন।")

	fun grade(
		exerciseType: String,
		answer: JsonObject,
		options: List<LocalExerciseOption>,
		acceptedAnswers: List<LocalExerciseAnswer>,
		hints: List<LocalHint>,
	): Verdict {
		val accepted = acceptedAnswers.map { it.acceptedAnswer }
		val correct = when (exerciseType) {
			"MCQ", "LISTENING" -> gradeMcq(answer, options)
			"TYPE_TRANSLATION", "FILL_BLANK" -> gradeText(answer, accepted)
			"WORD_BANK" -> gradeWordBank(answer, accepted)
			"MATCH" -> throw UnsupportedExerciseException("This exercise type is not gradable yet")
			else -> throw UnsupportedExerciseException("Unknown exercise type: $exerciseType")
		}
		if (correct) {
			return Verdict(correct = true, feedback = CORRECT_FEEDBACK)
		}
		return selectWrongFeedback(exerciseType, answer, hints)
	}

	private fun gradeMcq(answer: JsonObject, options: List<LocalExerciseOption>): Boolean {
		val selected = asString(answer["selectedOptionId"])
		return options.any { it.isCorrect && it.id == selected }
	}

	private fun gradeText(answer: JsonObject, accepted: List<String>): Boolean {
		val submitted = AnswerNormalizer.normalize(asString(answer["text"]))
		return accepted.map(AnswerNormalizer::normalize).any { it == submitted }
	}

	private fun gradeWordBank(answer: JsonObject, accepted: List<String>): Boolean {
		val tokens = answer["tokenOrder"] as? JsonArray ?: return false
		val assembled = AnswerNormalizer.normalize(
			tokens.fold("") { acc, token ->
				val text = asString(token)
				if (acc.isEmpty()) text else "$acc $text"
			},
		)
		return accepted.map(AnswerNormalizer::normalize).any { it == assembled }
	}

	/** Pick the most specific curated hint for a wrong answer; fall back to a generic message. */
	private fun selectWrongFeedback(exerciseType: String, answer: JsonObject, hints: List<LocalHint>): Verdict {
		val answerKey = wrongAnswerKey(exerciseType, answer)

		// 1. WRONG_ANSWER — an exact, curated mistake.
		for (hint in hints.filter { it.trigger == "WRONG_ANSWER" }) {
			if (answerKey != null && answerKey == AnswerNormalizer.normalize(hint.triggerKey)) {
				return Verdict(correct = false, feedback = toBilingual(hint), matchedPatternCode = null)
			}
		}
		// 2. PATTERN — an L1-transfer tip; report the pattern code.
		val patternHints = hints.filter { it.trigger == "PATTERN" }
		if (patternHints.isNotEmpty()) {
			val hint = patternHints.first()
			return Verdict(correct = false, feedback = toBilingual(hint), matchedPatternCode = hint.triggerKey)
		}
		// 3. DEFAULT — the fallback hint.
		val defaultHints = hints.filter { it.trigger == "DEFAULT" }
		if (defaultHints.isNotEmpty()) {
			return Verdict(correct = false, feedback = toBilingual(defaultHints.first()), matchedPatternCode = null)
		}
		return Verdict(correct = false, feedback = GENERIC_WRONG, matchedPatternCode = null)
	}

	/** The key a WRONG_ANSWER hint matches against (text for text types, raw option id for MCQ). */
	private fun wrongAnswerKey(exerciseType: String, answer: JsonObject): String? = when (exerciseType) {
		"TYPE_TRANSLATION", "FILL_BLANK" -> AnswerNormalizer.normalize(asString(answer["text"]))
		"MCQ", "LISTENING" -> asString(answer["selectedOptionId"])
		else -> null
	}

	private fun toBilingual(hint: LocalHint) = Bilingual(hint.textEn, hint.textBn)

	private fun asString(element: JsonElement?): String = when (element) {
		null, is JsonNull -> ""
		is JsonPrimitive -> element.content
		else -> element.toString()
	}
}
