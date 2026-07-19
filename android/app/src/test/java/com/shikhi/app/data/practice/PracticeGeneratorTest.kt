package com.shikhi.app.data.practice

import com.shikhi.app.data.content.db.LocalVocabulary
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Verbatim-behavior checks against the backend's `PracticeGeneratorTest`/`PracticeGenerator`
 * (`backend/src/main/java/com/shikhi/practice/service/PracticeGenerator.java`): the TYPE_CYCLE,
 * eligibility fallbacks (`canGap`/`canBuild`/`isTypeable`), and the two-pass MCQ distractor
 * algorithm.
 */
class PracticeGeneratorTest {

	private fun vocab(
		id: String,
		headword: String,
		partOfSpeech: String = "noun",
		cefrLevel: String = "A1",
		bnGloss: String = "গ্লস-$id",
		exampleEn: String? = "I see the $headword today.",
		exampleBn: String? = "আমি আজ $headword দেখি।",
	) = LocalVocabulary(
		id = id,
		headword = headword,
		senseLabel = null,
		partOfSpeech = partOfSpeech,
		cefrLevel = cefrLevel,
		bnGloss = bnGloss,
		exampleEn = exampleEn,
		exampleBn = exampleBn,
		ordinal = 1,
	)

	// ---- one exercise of each type, fully eligible word ------------------------------------

	@Test
	fun `WORD_MEANING carries bengali-gloss MCQ options and a matching answer key`() {
		val word = vocab("w1", "apple")
		val pool = listOf(vocab("d1", "banana"), vocab("d2", "cherry"), vocab("d3", "date"))
		val generator = PracticeGenerator(Random(1))

		val exercises = generator.generateRound(round = 1, words = listOf(word), distractorsByBand = mapOf("A1" to pool))
		val ex = exercises.single()

		assertEquals(PracticeExerciseType.WORD_MEANING, ex.type)
		assertEquals("What does “apple” mean?", ex.promptEn)
		assertEquals("“apple” শব্দের অর্থ কী?", ex.promptBn)
		val options = ex.payload["options"]!!.jsonArray
		assertEquals(4, options.size) // word + 3 distractors
		val correctId = ex.answerKey["correctOptionId"]!!.jsonPrimitive.content
		assertTrue(options.any { it.jsonObject["id"]!!.jsonPrimitive.content == correctId })
		val correctText = options.first { it.jsonObject["id"]!!.jsonPrimitive.content == correctId }.jsonObject["textEn"]!!.jsonPrimitive.content
		assertEquals(word.bnGloss, correctText) // WORD_MEANING options render the bnGloss
		assertEquals("apple — ${word.bnGloss}", ex.answerKey["revealText"]!!.jsonPrimitive.content)
	}

	@Test
	fun `MEANING_WORD carries headword MCQ options`() {
		val word = vocab("w1", "apple")
		val pool = listOf(vocab("d1", "banana"), vocab("d2", "cherry"), vocab("d3", "date"))
		val generator = PracticeGenerator(Random(1))

		// Index 2 in the cycle is MEANING_WORD.
		val exercises = generator.generateRound(1, List(3) { word }, mapOf("A1" to pool))
		val meaningWord = exercises[2]
		assertEquals(PracticeExerciseType.MEANING_WORD, meaningWord.type)
		val options = meaningWord.payload["options"]!!.jsonArray
		val correctId = meaningWord.answerKey["correctOptionId"]!!.jsonPrimitive.content
		val correctText = options.first { it.jsonObject["id"]!!.jsonPrimitive.content == correctId }.jsonObject["textEn"]!!.jsonPrimitive.content
		assertEquals("apple", correctText) // MEANING_WORD options render the headword
	}

	@Test
	fun `SENTENCE_GAP blanks the headword and carries a Bengali context hint`() {
		val word = vocab("w1", "apple", exampleEn = "I eat an apple every day.", exampleBn = "আমি প্রতিদিন একটি আপেল খাই।")
		val pool = listOf(vocab("d1", "banana"), vocab("d2", "cherry"))
		val generator = PracticeGenerator(Random(1))

		// Index 1 in the cycle is SENTENCE_GAP.
		val exercises = generator.generateRound(1, List(2) { word }, mapOf("A1" to pool))
		val gap = exercises[1]
		assertEquals(PracticeExerciseType.SENTENCE_GAP, gap.type)
		assertTrue(gap.promptEn.contains("____"))
		assertTrue(!gap.promptEn.contains("apple", ignoreCase = true))
		assertEquals(word.exampleBn, gap.payload["contextBn"]!!.jsonPrimitive.content)
		assertEquals(word.exampleEn, gap.answerKey["revealText"]!!.jsonPrimitive.content)
	}

	@Test
	fun `SENTENCE_BUILD tiles cover every token of the stripped sentence and the answer key accepts it verbatim`() {
		val word = vocab("w1", "apple", exampleEn = "I eat an apple every day.", exampleBn = "আমি প্রতিদিন একটি আপেল খাই।")
		val generator = PracticeGenerator(Random(1))

		// Index 3 in the cycle is SENTENCE_BUILD.
		val exercises = generator.generateRound(1, List(4) { word }, emptyMap())
		val build = exercises[3]
		assertEquals(PracticeExerciseType.SENTENCE_BUILD, build.type)
		val tiles = build.payload["tokens"]!!.jsonArray
		assertEquals(listOf("I", "eat", "an", "apple", "every", "day").toSet(), tiles.map { it.jsonObject["textEn"]!!.jsonPrimitive.content }.toSet())
		val accepted = build.answerKey["accepted"]!!.jsonArray.map { it.jsonPrimitive.content }
		assertEquals(listOf("I eat an apple every day"), accepted)
		assertEquals(word.exampleEn, build.answerKey["revealText"]!!.jsonPrimitive.content)
	}

	@Test
	fun `TYPE_WORD carries the part of speech and accepts only the exact headword`() {
		val word = vocab("w1", "apple", partOfSpeech = "noun")
		val generator = PracticeGenerator(Random(1))

		// Index 4 in the cycle is TYPE_WORD.
		val exercises = generator.generateRound(1, List(5) { word }, emptyMap())
		val typeWord = exercises[4]
		assertEquals(PracticeExerciseType.TYPE_WORD, typeWord.type)
		assertEquals("noun", typeWord.payload["partOfSpeech"]!!.jsonPrimitive.content)
		val accepted = typeWord.answerKey["accepted"]!!.jsonArray.map { it.jsonPrimitive.content }
		assertEquals(listOf("apple"), accepted)
	}

	// ---- eligibility fallbacks ---------------------------------------------------------------

	@Test
	fun `a multi-word headword falls back away from TYPE_WORD to MEANING_WORD`() {
		val word = vocab("w1", "as well as", exampleEn = "She likes tea as well as coffee.", exampleBn = "সে কফির পাশাপাশি চা পছন্দ করে।")
		val generator = PracticeGenerator(Random(1))

		val exercises = generator.generateRound(1, List(5) { word }, emptyMap())
		assertEquals(PracticeExerciseType.MEANING_WORD, exercises[4].type)
	}

	@Test
	fun `a multi-word headword falls back away from SENTENCE_GAP to SENTENCE_BUILD when the sentence is short enough`() {
		val word = vocab("w1", "as well as", exampleEn = "She likes tea as well as coffee.", exampleBn = "সে কফির পাশাপাশি চা পছন্দ করে।")
		val generator = PracticeGenerator(Random(1))

		val exercises = generator.generateRound(1, List(2) { word }, emptyMap())
		assertEquals(PracticeExerciseType.SENTENCE_BUILD, exercises[1].type)
	}

	@Test
	fun `a word with no example sentence falls all the way back to WORD_MEANING for sentence-level slots`() {
		val word = vocab("w1", "apple", exampleEn = null, exampleBn = null)
		val pool = listOf(vocab("d1", "banana"))
		val generator = PracticeGenerator(Random(1))

		val exercises = generator.generateRound(1, List(4) { word }, mapOf("A1" to pool))
		assertEquals(PracticeExerciseType.WORD_MEANING, exercises[1].type) // was SENTENCE_GAP
		assertEquals(PracticeExerciseType.WORD_MEANING, exercises[3].type) // was SENTENCE_BUILD
	}

	@Test
	fun `a sentence longer than MAX_BUILD_TOKENS falls back from SENTENCE_BUILD to SENTENCE_GAP`() {
		val word = vocab(
			"w1",
			"weather",
			exampleEn = "The weather today is absolutely wonderful and quite sunny outside now.",
			exampleBn = "আজকের আবহাওয়া অসাধারণ।",
		)
		val pool = listOf(vocab("d1", "banana"))
		val generator = PracticeGenerator(Random(1))

		val exercises = generator.generateRound(1, List(4) { word }, mapOf("A1" to pool))
		assertEquals(PracticeExerciseType.SENTENCE_GAP, exercises[3].type)
	}

	@Test
	fun `a sentence with fewer than MIN_BUILD_TOKENS falls back from SENTENCE_BUILD to SENTENCE_GAP`() {
		val word = vocab("w1", "run", exampleEn = "I run.", exampleBn = "আমি দৌড়াই।")
		val pool = listOf(vocab("d1", "banana"))
		val generator = PracticeGenerator(Random(1))

		val exercises = generator.generateRound(1, List(4) { word }, mapOf("A1" to pool))
		assertEquals(PracticeExerciseType.SENTENCE_GAP, exercises[3].type)
	}

	// ---- MCQ distractor selection -------------------------------------------------------------

	@Test
	fun `mcq distractors prefer the same part of speech before falling back to any part of speech`() {
		val word = vocab("w1", "apple", partOfSpeech = "noun")
		val pool = listOf(
			vocab("v1", "run", partOfSpeech = "verb"),
			vocab("n1", "banana", partOfSpeech = "noun"),
			vocab("n2", "cherry", partOfSpeech = "noun"),
			vocab("adj1", "big", partOfSpeech = "adj"),
		)
		val generator = PracticeGenerator(Random(42))

		val ex = generator.generateRound(1, listOf(word), mapOf("A1" to pool)).single()
		val optionTexts = ex.payload["options"]!!.jsonArray.map { it.jsonObject["textEn"]!!.jsonPrimitive.content }.toSet()
		// Word's own gloss + the two same-POS distractors (banana, cherry) must be present before
		// the off-POS ones (run/big) are needed, since the pool has exactly two same-POS options.
		assertTrue(optionTexts.contains(word.bnGloss))
		assertEquals(4, optionTexts.size)
		val distractorTexts = optionTexts - word.bnGloss
		val sameBandGlosses = setOf(pool[1].bnGloss, pool[2].bnGloss) // banana, cherry (noun)
		assertTrue("expected the 2 same-POS distractors to be used first", distractorTexts.containsAll(sameBandGlosses))
	}

	@Test
	fun `mcq falls back to any part of speech when same-POS candidates run out`() {
		val word = vocab("w1", "apple", partOfSpeech = "noun")
		val pool = listOf(
			vocab("v1", "run", partOfSpeech = "verb"),
			vocab("adj1", "big", partOfSpeech = "adj"),
			vocab("adv1", "quickly", partOfSpeech = "adv"),
		)
		val generator = PracticeGenerator(Random(7))

		val ex = generator.generateRound(1, listOf(word), mapOf("A1" to pool)).single()
		val options = ex.payload["options"]!!.jsonArray
		assertEquals(4, options.size) // word + all 3 off-POS distractors, since none share the POS
	}

	@Test
	fun `mcq never duplicates option text and shrinks gracefully when the pool is small`() {
		val word = vocab("w1", "apple", partOfSpeech = "noun", bnGloss = "আপেল")
		val pool = listOf(vocab("d1", "banana", bnGloss = "আপেল")) // duplicate gloss text on purpose
		val generator = PracticeGenerator(Random(3))

		val ex = generator.generateRound(1, listOf(word), mapOf("A1" to pool)).single()
		val texts = ex.payload["options"]!!.jsonArray.map { it.jsonObject["textEn"]!!.jsonPrimitive.content }
		assertEquals(1, texts.size) // only the word itself; the duplicate-text distractor is skipped
	}

	@Test
	fun `an empty distractor pool still yields a gradable single-option exercise`() {
		val word = vocab("w1", "apple")
		val generator = PracticeGenerator(Random(0))

		val ex = generator.generateRound(1, listOf(word), emptyMap()).single()
		val options = ex.payload["options"]!!.jsonArray
		assertEquals(1, options.size)
		assertEquals(word.bnGloss, options[0].jsonObject["textEn"]!!.jsonPrimitive.content)
	}
}
