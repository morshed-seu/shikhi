package com.shikhi.app.data.practice

import com.shikhi.app.data.content.db.LocalVocabulary
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.UUID
import kotlin.random.Random

/**
 * One generated exercise, persisted as a [com.shikhi.app.data.db.LocalPracticeExercise] row.
 * `payload` is the learner-visible config; `answerKey` is grading-only and must never reach the
 * UI layer directly (same "correctness never leaves the answer key" rule as the backend and as
 * OF3's [com.shikhi.app.data.content.db.ContentAnswerKeyDao] split) — see
 * [LocalPracticeSource.toWireExercise], which strips it when building the learner-facing DTO.
 */
data class GeneratedExercise(
	val round: Int,
	val ordinal: Int,
	val vocabularyId: String,
	val type: String,
	val promptEn: String,
	val promptBn: String,
	val payload: JsonObject,
	val answerKey: JsonObject,
)

/**
 * Verbatim-behavior Kotlin port of the backend's `PracticeGenerator`
 * (`backend/src/main/java/com/shikhi/practice/service/PracticeGenerator.java`, docs/
 * 93-offline-learning-design.md §4.3): pure templating, no I/O, so a round of exercises is fully
 * deterministic given its inputs and [random]. Prompt/feedback string templates (English and
 * Bengali) are copied verbatim — these are user-visible text.
 */
class PracticeGenerator(private val random: Random = Random.Default) {

	/** Rounds cycle this sequence (word, sentence, word, sentence, word -> 60/40 over 10). */
	private val typeCycle = listOf(
		PracticeExerciseType.WORD_MEANING,
		PracticeExerciseType.SENTENCE_GAP,
		PracticeExerciseType.MEANING_WORD,
		PracticeExerciseType.SENTENCE_BUILD,
		PracticeExerciseType.TYPE_WORD,
	)

	/**
	 * Build one round of exercises for [words], drawing MCQ distractors from
	 * [distractorsByBand] (same-band words, any size — filtered per exercise).
	 */
	fun generateRound(
		round: Int,
		words: List<LocalVocabulary>,
		distractorsByBand: Map<String, List<LocalVocabulary>>,
	): List<GeneratedExercise> =
		words.mapIndexed { i, word ->
			val type = chooseType(typeCycle[i % typeCycle.size], word)
			val pool = distractorsByBand[word.cefrLevel].orEmpty()
			build(round, i + 1, word, type, pool)
		}

	/** Fall back to an always-eligible word-level format when a word can't carry the type. */
	private fun chooseType(desired: String, word: LocalVocabulary): String = when (desired) {
		PracticeExerciseType.SENTENCE_GAP -> when {
			canGap(word) -> desired
			canBuild(word) -> PracticeExerciseType.SENTENCE_BUILD
			else -> PracticeExerciseType.WORD_MEANING
		}
		PracticeExerciseType.SENTENCE_BUILD -> when {
			canBuild(word) -> desired
			canGap(word) -> PracticeExerciseType.SENTENCE_GAP
			else -> PracticeExerciseType.WORD_MEANING
		}
		PracticeExerciseType.TYPE_WORD -> if (isTypeable(word)) desired else PracticeExerciseType.MEANING_WORD
		else -> desired // WORD_MEANING, MEANING_WORD
	}

	private fun build(
		round: Int,
		ordinal: Int,
		word: LocalVocabulary,
		type: String,
		pool: List<LocalVocabulary>,
	): GeneratedExercise = when (type) {
		PracticeExerciseType.WORD_MEANING -> wordMeaning(round, ordinal, word, pool)
		PracticeExerciseType.MEANING_WORD -> meaningWord(round, ordinal, word, pool)
		PracticeExerciseType.SENTENCE_GAP -> sentenceGap(round, ordinal, word, pool)
		PracticeExerciseType.SENTENCE_BUILD -> sentenceBuild(round, ordinal, word)
		PracticeExerciseType.TYPE_WORD -> typeWord(round, ordinal, word)
		else -> error("Unknown practice exercise type: $type")
	}

	// ---- word-level formats -------------------------------------------------------------

	private fun wordMeaning(round: Int, ordinal: Int, word: LocalVocabulary, pool: List<LocalVocabulary>): GeneratedExercise {
		val options = mcqOptions(word, pool) { it.bnGloss }
		val payload = buildJsonObject { put("options", options.toJson()) }
		return GeneratedExercise(
			round, ordinal, word.id, PracticeExerciseType.WORD_MEANING,
			promptEn = "What does “${word.headword}” mean?",
			promptBn = "“${word.headword}” শব্দের অর্থ কী?",
			payload = payload,
			answerKey = mcqKey(options, "${word.headword} — ${word.bnGloss}"),
		)
	}

	private fun meaningWord(round: Int, ordinal: Int, word: LocalVocabulary, pool: List<LocalVocabulary>): GeneratedExercise {
		val options = mcqOptions(word, pool) { it.headword }
		val payload = buildJsonObject { put("options", options.toJson()) }
		return GeneratedExercise(
			round, ordinal, word.id, PracticeExerciseType.MEANING_WORD,
			promptEn = "Which English word means “${word.bnGloss}”?",
			promptBn = "“${word.bnGloss}” — এর ইংরেজি শব্দ কোনটি?",
			payload = payload,
			answerKey = mcqKey(options, "${word.headword} — ${word.bnGloss}"),
		)
	}

	private fun typeWord(round: Int, ordinal: Int, word: LocalVocabulary): GeneratedExercise {
		val payload = buildJsonObject { put("partOfSpeech", word.partOfSpeech) }
		val key = buildJsonObject {
			putJsonArray("accepted") { add(word.headword) }
			put("revealText", "${word.headword} — ${word.bnGloss}")
		}
		return GeneratedExercise(
			round, ordinal, word.id, PracticeExerciseType.TYPE_WORD,
			promptEn = "Type the English word for “${word.bnGloss}”",
			promptBn = "“${word.bnGloss}” — এর ইংরেজি শব্দটি লিখুন",
			payload = payload,
			answerKey = key,
		)
	}

	// ---- sentence-level formats (short sentences only) -----------------------------------

	private fun sentenceGap(round: Int, ordinal: Int, word: LocalVocabulary, pool: List<LocalVocabulary>): GeneratedExercise {
		val exampleEn = requireNotNull(word.exampleEn)
		val blanked = headwordPattern(word).replaceFirst(exampleEn, "____")
		val options = mcqOptions(word, pool) { it.headword }
		val payload = buildJsonObject {
			put("options", options.toJson())
			put("contextBn", word.exampleBn)
		}
		return GeneratedExercise(
			round, ordinal, word.id, PracticeExerciseType.SENTENCE_GAP,
			promptEn = "Fill in the blank: “$blanked”",
			promptBn = "শূন্যস্থান পূরণ করুন: “$blanked”",
			payload = payload,
			answerKey = mcqKey(options, exampleEn),
		)
	}

	private fun sentenceBuild(round: Int, ordinal: Int, word: LocalVocabulary): GeneratedExercise {
		val sentence = stripTerminalPunctuation(requireNotNull(word.exampleEn))
		val tokens = sentence.split(Regex("\\s+"))
		val tiles = tokens.map { token -> TileId(UUID.randomUUID().toString(), token) }.shuffled(random)
		val payload = buildJsonObject {
			putJsonArray("tokens") {
				tiles.forEach { tile ->
					add(
						buildJsonObject {
							put("id", tile.id)
							put("textEn", tile.text)
							put("textBn", tile.text)
						},
					)
				}
			}
			put("targetBn", word.exampleBn)
		}
		val key = buildJsonObject {
			putJsonArray("accepted") { add(sentence) }
			put("revealText", word.exampleEn)
		}
		return GeneratedExercise(
			round, ordinal, word.id, PracticeExerciseType.SENTENCE_BUILD,
			promptEn = "Build the sentence: “${word.exampleBn}”",
			promptBn = "শব্দ সাজিয়ে বাক্যটি তৈরি করুন: “${word.exampleBn}”",
			payload = payload,
			answerKey = key,
		)
	}

	// ---- eligibility ----------------------------------------------------------------------

	private fun canGap(word: LocalVocabulary): Boolean {
		val exampleEn = word.exampleEn ?: return false
		if (word.exampleBn == null || !isTypeable(word)) return false
		return headwordPattern(word).containsMatchIn(exampleEn)
	}

	private fun canBuild(word: LocalVocabulary): Boolean {
		val exampleEn = word.exampleEn ?: return false
		if (word.exampleBn == null) return false
		val tokenCount = stripTerminalPunctuation(exampleEn).split(Regex("\\s+")).size
		return tokenCount in MIN_BUILD_TOKENS..MAX_BUILD_TOKENS
	}

	/** Single-token headwords only ("advice", not "a, an") — anything a learner can type. */
	private fun isTypeable(word: LocalVocabulary): Boolean =
		!word.headword.contains(" ") && !word.headword.contains(",")

	private fun headwordPattern(word: LocalVocabulary): Regex =
		Regex("\\b${Regex.escape(word.headword)}\\b", RegexOption.IGNORE_CASE)

	private fun stripTerminalPunctuation(sentence: String): String =
		sentence.replace(Regex("[.!?।]+$"), "").trim()

	// ---- MCQ assembly ---------------------------------------------------------------------

	private data class TileId(val id: String, val text: String)

	private data class OptionEntry(val id: String, val text: String)

	private data class Options(val entries: List<OptionEntry>, val correctId: String) {
		fun toJson() = buildJsonArray {
			entries.forEach { e ->
				add(
					buildJsonObject {
						put("id", e.id)
						put("textEn", e.text)
						put("textBn", e.text)
					},
				)
			}
		}
	}

	/**
	 * Correct value + up to three same-band distractors (same part of speech preferred, duplicate
	 * texts skipped), shuffled. The correct flag never enters the payload — only the answer key
	 * knows [Options.correctId].
	 */
	private fun mcqOptions(word: LocalVocabulary, pool: List<LocalVocabulary>, text: (LocalVocabulary) -> String): Options {
		val distractors = mutableListOf<LocalVocabulary>()
		val taken = mutableSetOf(text(word))
		// Two passes: same part of speech first, then anything still needed. A text only enters
		// `taken` when actually used, so pass 2 can still pick pass-1 rejects.
		for (samePosOnly in booleanArrayOf(true, false)) {
			for (candidate in pool) {
				if (distractors.size == MCQ_DISTRACTORS) break
				if (candidate.id == word.id ||
					(samePosOnly && candidate.partOfSpeech != word.partOfSpeech) ||
					!taken.add(text(candidate))
				) {
					continue
				}
				distractors.add(candidate)
			}
		}

		val correctId = UUID.randomUUID().toString()
		val entries = mutableListOf(OptionEntry(correctId, text(word)))
		distractors.forEach { entries.add(OptionEntry(UUID.randomUUID().toString(), text(it))) }
		entries.shuffle(random)
		return Options(entries, correctId)
	}

	private fun mcqKey(options: Options, revealText: String): JsonObject = buildJsonObject {
		put("correctOptionId", options.correctId)
		put("revealText", revealText)
	}

	companion object {
		/** SENTENCE_BUILD only for genuinely small sentences (word-bank stays tappable). */
		const val MAX_BUILD_TOKENS = 8
		private const val MIN_BUILD_TOKENS = 3
		private const val MCQ_DISTRACTORS = 3
	}
}
