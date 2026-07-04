package com.shikhi.practice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.content.domain.Vocabulary;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.domain.PracticeExerciseType;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The generator is pure (no I/O), so the mix rules are asserted directly: sentence formats
 * only for short examples, word-level fallbacks, and — critically — that correctness never
 * appears in the learner-visible payload (it lives only in the answer key).
 */
class PracticeGeneratorTest {

	private final PracticeGenerator generator = new PracticeGenerator(new Random(42));

	private final UUID sessionId = UUID.randomUUID();

	/** Vocabulary has a JPA-only constructor; tests build fixtures reflectively. */
	private Vocabulary word(String headword, String pos, String band, String gloss,
			String exampleEn, String exampleBn) {
		try {
			var ctor = Vocabulary.class.getDeclaredConstructor();
			ctor.setAccessible(true);
			Vocabulary v = ctor.newInstance();
			ReflectionTestUtils.setField(v, "id", UUID.randomUUID());
			ReflectionTestUtils.setField(v, "headword", headword);
			ReflectionTestUtils.setField(v, "partOfSpeech", pos);
			ReflectionTestUtils.setField(v, "cefrLevel", band);
			ReflectionTestUtils.setField(v, "bnGloss", gloss);
			ReflectionTestUtils.setField(v, "exampleEn", exampleEn);
			ReflectionTestUtils.setField(v, "exampleBn", exampleBn);
			return v;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private Map<String, List<Vocabulary>> pool(String band) {
		return Map.of(band, List.of(
				word("apple", "n.", band, "আপেল", null, null),
				word("dog", "n.", band, "কুকুর", null, null),
				word("book", "n.", band, "বই", null, null),
				word("run", "v.", band, "দৌড়ানো", null, null)));
	}

	@Test
	void sentenceSlotsFallBackToWordLevelWithoutAnExample() {
		List<Vocabulary> words = List.of(
				word("advice", "n.", "A1", "পরামর্শ", null, null),
				word("air", "n.", "A1", "বাতাস", null, null), // SENTENCE_GAP slot
				word("answer", "n.", "A1", "উত্তর", null, null),
				word("area", "n.", "A1", "এলাকা", null, null), // SENTENCE_BUILD slot
				word("art", "n.", "A1", "শিল্প", null, null)); // TYPE_WORD slot (typeable)

		List<PracticeExercise> round = generator.generateRound(sessionId, 1, words, pool("A1"));

		assertThat(round).extracting(PracticeExercise::getType).containsExactly(
				PracticeExerciseType.WORD_MEANING,
				PracticeExerciseType.WORD_MEANING, // gap slot degraded: no example
				PracticeExerciseType.MEANING_WORD,
				PracticeExerciseType.WORD_MEANING, // build slot degraded: no example
				PracticeExerciseType.TYPE_WORD);
	}

	@Test
	void shortExamplesProduceSentenceFormats() {
		String en = "The air is fresh here.";
		String bn = "এখানকার বাতাস তাজা।";
		List<Vocabulary> words = List.of(
				word("advice", "n.", "A1", "পরামর্শ", "She gave me good advice.", "তিনি আমাকে ভালো পরামর্শ দিলেন।"),
				word("air", "n.", "A1", "বাতাস", en, bn),
				word("answer", "n.", "A1", "উত্তর", "I know the answer.", "আমি উত্তরটি জানি।"),
				word("area", "n.", "A1", "এলাকা", "This is a quiet area.", "এটি একটি শান্ত এলাকা।"),
				word("art", "n.", "A1", "শিল্প", "She loves art.", "সে শিল্প ভালোবাসে।"));

		List<PracticeExercise> round = generator.generateRound(sessionId, 1, words, pool("A1"));

		assertThat(round.get(1).getType()).isEqualTo(PracticeExerciseType.SENTENCE_GAP);
		assertThat(round.get(1).getPromptEn()).contains("____").doesNotContain("air");
		assertThat(round.get(3).getType()).isEqualTo(PracticeExerciseType.SENTENCE_BUILD);
		assertThat(round.get(4).getType()).isEqualTo(PracticeExerciseType.TYPE_WORD);
	}

	@Test
	void longExamplesNeverBecomeWordBank() {
		List<Vocabulary> words = List.of(
				word("w1", "n.", "A1", "গ১", null, null),
				word("w2", "n.", "A1", "গ২", null, null),
				word("w3", "n.", "A1", "গ৩", null, null),
				// SENTENCE_BUILD slot, but the example is longer than MAX_BUILD_TOKENS words.
				word("holiday", "n.", "A1", "ছুটি",
						"We are going on a very long holiday to the beautiful hills next year.",
						"আমরা আগামী বছর পাহাড়ে লম্বা ছুটিতে যাচ্ছি।"));

		List<PracticeExercise> round = generator.generateRound(sessionId, 1, words, pool("A1"));

		// Falls back to SENTENCE_GAP (example exists and contains the headword).
		assertThat(round.get(3).getType()).isEqualTo(PracticeExerciseType.SENTENCE_GAP);
	}

	@Test
	void multiWordHeadwordsAreNeverTypeWord() {
		List<Vocabulary> words = List.of(
				word("w1", "n.", "A1", "গ১", null, null),
				word("w2", "n.", "A1", "গ২", null, null),
				word("w3", "n.", "A1", "গ৩", null, null),
				word("w4", "n.", "A1", "গ৪", null, null),
				// TYPE_WORD slot, but "a, an" cannot be typed as one word.
				word("a, an", "indefinite article", "A1", "একটি", "I have a pen.", "আমার একটি কলম আছে।"));

		List<PracticeExercise> round = generator.generateRound(sessionId, 1, words, pool("A1"));

		assertThat(round.get(4).getType()).isEqualTo(PracticeExerciseType.MEANING_WORD);
	}

	@Test
	@SuppressWarnings("unchecked")
	void payloadNeverLeaksCorrectness() {
		List<Vocabulary> words = List.of(
				word("advice", "n.", "A1", "পরামর্শ", "She gave me good advice.", "তিনি আমাকে ভালো পরামর্শ দিলেন।"),
				word("air", "n.", "A1", "বাতাস", "The air is fresh here.", "এখানকার বাতাস তাজা।"),
				word("answer", "n.", "A1", "উত্তর", "I know the answer.", "আমি উত্তরটি জানি।"),
				word("area", "n.", "A1", "এলাকা", "This is a quiet area.", "এটি একটি শান্ত এলাকা।"),
				word("art", "n.", "A1", "শিল্প", "She loves art.", "সে শিল্প ভালোবাসে।"));

		for (PracticeExercise exercise : generator.generateRound(sessionId, 1, words, pool("A1"))) {
			assertThat(exercise.getPayload().toString()).doesNotContain("correct");
			assertThat(exercise.getAnswerKey()).isNotEmpty();

			Object options = exercise.getPayload().get("options");
			if (options instanceof List<?> list) {
				// The answer key's correct id must be one of the option ids — and option
				// entries must carry nothing but id + text.
				Set<String> ids = list.stream()
						.map(o -> (Map<String, Object>) o)
						.peek(o -> assertThat(o.keySet()).containsOnly("id", "text"))
						.map(o -> o.get("id").toString())
						.collect(Collectors.toSet());
				assertThat(ids).contains(exercise.getAnswerKey().get("correctOptionId").toString());
			}
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void mcqOptionTextsAreUniqueAndIncludeTheCorrectAnswer() {
		Vocabulary target = word("advice", "n.", "A1", "পরামর্শ", null, null);
		List<PracticeExercise> round = generator.generateRound(sessionId, 1, List.of(target),
				pool("A1"));

		List<Map<String, Object>> options =
				(List<Map<String, Object>>) round.getFirst().getPayload().get("options");
		List<String> texts = options.stream()
				.map(o -> ((Map<String, String>) o.get("text")).get("en"))
				.toList();

		assertThat(options).hasSize(4); // correct + 3 distractors from the pool
		assertThat(texts).doesNotHaveDuplicates().contains("পরামর্শ");
	}

	@Test
	@SuppressWarnings("unchecked")
	void mcqStillFillsOptionsWhenNoPoolWordSharesThePartOfSpeech() {
		// Regression: pass 1 (same POS) must not consume candidates it rejects —
		// pass 2 should still be able to use them as distractors.
		Vocabulary target = word("advice", "n.", "A1", "পরামর্শ", null, null);
		Map<String, List<Vocabulary>> pool = Map.of("A1", List.of(
				word("run", "v.", "A1", "দৌড়ানো", null, null),
				word("happy", "adj.", "A1", "খুশি", null, null),
				word("slowly", "adv.", "A1", "ধীরে", null, null)));

		List<PracticeExercise> round = generator.generateRound(sessionId, 1, List.of(target),
				pool);

		List<Map<String, Object>> options =
				(List<Map<String, Object>>) round.getFirst().getPayload().get("options");
		assertThat(options).hasSize(4);
	}
}
