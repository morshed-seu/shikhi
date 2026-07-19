package com.shikhi.app.data.practice

import com.shikhi.app.data.api.dto.Bilingual
import com.shikhi.app.data.api.dto.Verdict
import com.shikhi.app.data.lesson.grading.AnswerNormalizer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Verbatim-behavior Kotlin port of the backend's `PracticeSessionService.grade()`/`verdict()`
 * (`backend/src/main/java/com/shikhi/practice/service/PracticeSessionService.java`, docs/
 * 93-offline-learning-design.md §4.3): MCQ types compare `selectedOptionId` against the answer
 * key's `correctOptionId`; `SENTENCE_BUILD`/`TYPE_WORD` normalize + match against `accepted`.
 * Reuses [AnswerNormalizer] (OF3) — same normalization rule as lesson grading, not duplicated.
 */
object PracticeGrading {

	private val CORRECT_FEEDBACK = Bilingual("Correct!", "সঠিক!")

	fun grade(type: String, answer: JsonObject, answerKey: JsonObject): Boolean = when (type) {
		PracticeExerciseType.WORD_MEANING, PracticeExerciseType.MEANING_WORD, PracticeExerciseType.SENTENCE_GAP -> {
			val selected = asString(answer["selectedOptionId"])
			selected == asString(answerKey["correctOptionId"])
		}

		PracticeExerciseType.SENTENCE_BUILD -> {
			val tokens = answer["tokenOrder"] as? JsonArray
			if (tokens == null) {
				false
			} else {
				val assembled = AnswerNormalizer.normalize(tokens.joinToString(" ") { asString(it) })
				acceptedAnswers(answerKey).map(AnswerNormalizer::normalize).any { it == assembled }
			}
		}

		PracticeExerciseType.TYPE_WORD -> {
			val submitted = AnswerNormalizer.normalize(asString(answer["text"]))
			acceptedAnswers(answerKey).map(AnswerNormalizer::normalize).any { it == submitted }
		}

		else -> false
	}

	/** Wrong answers reveal the right one — the moment of learning (US-12.5). */
	fun verdict(correct: Boolean, answerKey: JsonObject): Verdict {
		if (correct) return Verdict(correct = true, feedback = CORRECT_FEEDBACK)
		val reveal = asString(answerKey["revealText"])
		return Verdict(
			correct = false,
			feedback = Bilingual("Correct answer: $reveal", "সঠিক উত্তর: $reveal"),
		)
	}

	private fun acceptedAnswers(answerKey: JsonObject): List<String> {
		val accepted = answerKey["accepted"] as? JsonArray ?: return emptyList()
		return accepted.map { asString(it) }
	}

	private fun asString(element: JsonElement?): String = when (element) {
		null, is JsonNull -> ""
		is JsonPrimitive -> element.content
		else -> element.toString()
	}
}
