package com.shikhi.practice.service;

import com.shikhi.content.domain.Vocabulary;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.domain.PracticeExerciseType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Turns picked vocabulary into concrete exercises (E12, LLD §2.8). Pure templating — no
 * I/O — so the mix rules are unit-testable: a round cycles word-level and sentence-level
 * formats (~60/40), and sentence formats are only chosen when the word's example sentence
 * is short (US-12.5, "smaller sentences"). Correctness goes into the server-only answer
 * key; the payload holds only what the learner may see.
 */
@Component
public class PracticeGenerator {

	/** Rounds cycle this sequence (word, sentence, word, sentence, word → 60/40 over 10). */
	private static final PracticeExerciseType[] TYPE_CYCLE = {
			PracticeExerciseType.WORD_MEANING, PracticeExerciseType.SENTENCE_GAP,
			PracticeExerciseType.MEANING_WORD, PracticeExerciseType.SENTENCE_BUILD,
			PracticeExerciseType.TYPE_WORD };

	/** SENTENCE_BUILD only for genuinely small sentences (word-bank stays tappable). */
	static final int MAX_BUILD_TOKENS = 8;
	private static final int MIN_BUILD_TOKENS = 3;
	private static final int MCQ_DISTRACTORS = 3;

	private final Random random;

	public PracticeGenerator() {
		this(new Random());
	}

	PracticeGenerator(Random random) {
		this.random = random;
	}

	/**
	 * Build one round of exercises for {@code words}, drawing MCQ distractors from
	 * {@code distractorsByBand} (same-band words, any size — filtered per exercise).
	 */
	public List<PracticeExercise> generateRound(UUID sessionId, int round, List<Vocabulary> words,
			Map<String, List<Vocabulary>> distractorsByBand) {
		List<PracticeExercise> exercises = new ArrayList<>(words.size());
		for (int i = 0; i < words.size(); i++) {
			Vocabulary word = words.get(i);
			PracticeExerciseType type = chooseType(TYPE_CYCLE[i % TYPE_CYCLE.length], word);
			List<Vocabulary> pool = distractorsByBand.getOrDefault(word.getCefrLevel(), List.of());
			exercises.add(build(sessionId, round, i + 1, word, type, pool));
		}
		return exercises;
	}

	/** Fall back to an always-eligible word-level format when a word can't carry the type. */
	private PracticeExerciseType chooseType(PracticeExerciseType desired, Vocabulary word) {
		return switch (desired) {
			case SENTENCE_GAP -> canGap(word) ? desired
					: canBuild(word) ? PracticeExerciseType.SENTENCE_BUILD
					: PracticeExerciseType.WORD_MEANING;
			case SENTENCE_BUILD -> canBuild(word) ? desired
					: canGap(word) ? PracticeExerciseType.SENTENCE_GAP
					: PracticeExerciseType.WORD_MEANING;
			case TYPE_WORD -> isTypeable(word) ? desired : PracticeExerciseType.MEANING_WORD;
			case WORD_MEANING, MEANING_WORD -> desired;
		};
	}

	private PracticeExercise build(UUID sessionId, int round, int ordinal, Vocabulary word,
			PracticeExerciseType type, List<Vocabulary> pool) {
		return switch (type) {
			case WORD_MEANING -> wordMeaning(sessionId, round, ordinal, word, pool);
			case MEANING_WORD -> meaningWord(sessionId, round, ordinal, word, pool);
			case SENTENCE_GAP -> sentenceGap(sessionId, round, ordinal, word, pool);
			case SENTENCE_BUILD -> sentenceBuild(sessionId, round, ordinal, word);
			case TYPE_WORD -> typeWord(sessionId, round, ordinal, word);
		};
	}

	// ---- word-level formats -------------------------------------------------------------

	private PracticeExercise wordMeaning(UUID sessionId, int round, int ordinal, Vocabulary word,
			List<Vocabulary> pool) {
		Options options = mcqOptions(word, pool, Vocabulary::getBnGloss);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("options", options.entries());
		return new PracticeExercise(sessionId, round, ordinal, word.getId(),
				PracticeExerciseType.WORD_MEANING,
				"What does “%s” mean?".formatted(word.getHeadword()),
				"“%s” শব্দের অর্থ কী?".formatted(word.getHeadword()),
				payload, mcqKey(options, word.getHeadword() + " — " + word.getBnGloss()));
	}

	private PracticeExercise meaningWord(UUID sessionId, int round, int ordinal, Vocabulary word,
			List<Vocabulary> pool) {
		Options options = mcqOptions(word, pool, Vocabulary::getHeadword);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("options", options.entries());
		return new PracticeExercise(sessionId, round, ordinal, word.getId(),
				PracticeExerciseType.MEANING_WORD,
				"Which English word means “%s”?".formatted(word.getBnGloss()),
				"“%s” — এর ইংরেজি শব্দ কোনটি?".formatted(word.getBnGloss()),
				payload, mcqKey(options, word.getHeadword() + " — " + word.getBnGloss()));
	}

	private PracticeExercise typeWord(UUID sessionId, int round, int ordinal, Vocabulary word) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("partOfSpeech", word.getPartOfSpeech());
		Map<String, Object> key = new LinkedHashMap<>();
		key.put("accepted", List.of(word.getHeadword()));
		key.put("revealText", word.getHeadword() + " — " + word.getBnGloss());
		return new PracticeExercise(sessionId, round, ordinal, word.getId(),
				PracticeExerciseType.TYPE_WORD,
				"Type the English word for “%s”".formatted(word.getBnGloss()),
				"“%s” — এর ইংরেজি শব্দটি লিখুন".formatted(word.getBnGloss()),
				payload, key);
	}

	// ---- sentence-level formats (short sentences only) -----------------------------------

	private PracticeExercise sentenceGap(UUID sessionId, int round, int ordinal, Vocabulary word,
			List<Vocabulary> pool) {
		String blanked = headwordPattern(word).matcher(word.getExampleEn()).replaceFirst("____");
		Options options = mcqOptions(word, pool, Vocabulary::getHeadword);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("options", options.entries());
		payload.put("contextBn", word.getExampleBn());
		return new PracticeExercise(sessionId, round, ordinal, word.getId(),
				PracticeExerciseType.SENTENCE_GAP,
				"Fill in the blank: “%s”".formatted(blanked),
				"শূন্যস্থান পূরণ করুন: “%s”".formatted(blanked),
				payload, mcqKey(options, word.getExampleEn()));
	}

	private PracticeExercise sentenceBuild(UUID sessionId, int round, int ordinal,
			Vocabulary word) {
		String sentence = stripTerminalPunctuation(word.getExampleEn());
		List<String> tokens = new ArrayList<>(List.of(sentence.split("\\s+")));
		List<Map<String, Object>> tiles = new ArrayList<>(tokens.size());
		for (String token : tokens) {
			tiles.add(Map.of("id", UUID.randomUUID().toString(),
					"text", Map.of("en", token, "bn", token)));
		}
		java.util.Collections.shuffle(tiles, random);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("tokens", tiles);
		payload.put("targetBn", word.getExampleBn());
		Map<String, Object> key = new LinkedHashMap<>();
		key.put("accepted", List.of(sentence));
		key.put("revealText", word.getExampleEn());
		return new PracticeExercise(sessionId, round, ordinal, word.getId(),
				PracticeExerciseType.SENTENCE_BUILD,
				"Build the sentence: “%s”".formatted(word.getExampleBn()),
				"শব্দ সাজিয়ে বাক্যটি তৈরি করুন: “%s”".formatted(word.getExampleBn()),
				payload, key);
	}

	// ---- eligibility ----------------------------------------------------------------------

	private boolean canGap(Vocabulary word) {
		return word.getExampleEn() != null && word.getExampleBn() != null
				&& isTypeable(word)
				&& headwordPattern(word).matcher(word.getExampleEn()).find();
	}

	private boolean canBuild(Vocabulary word) {
		if (word.getExampleEn() == null || word.getExampleBn() == null) {
			return false;
		}
		int tokens = stripTerminalPunctuation(word.getExampleEn()).split("\\s+").length;
		return tokens >= MIN_BUILD_TOKENS && tokens <= MAX_BUILD_TOKENS;
	}

	/** Single-token headwords only ("advice", not "a, an") — anything a learner can type. */
	private boolean isTypeable(Vocabulary word) {
		return !word.getHeadword().contains(" ") && !word.getHeadword().contains(",");
	}

	private Pattern headwordPattern(Vocabulary word) {
		return Pattern.compile("\\b" + Pattern.quote(word.getHeadword()) + "\\b",
				Pattern.CASE_INSENSITIVE);
	}

	private String stripTerminalPunctuation(String sentence) {
		return sentence.replaceAll("[.!?।]+$", "").trim();
	}

	// ---- MCQ assembly ---------------------------------------------------------------------

	private record Options(List<Map<String, Object>> entries, String correctId) {
	}

	/**
	 * Correct value + up to three same-band distractors (same part of speech preferred,
	 * duplicate texts skipped), shuffled. The correct flag never enters the payload — only
	 * the answer key knows {@code correctId}.
	 */
	private Options mcqOptions(Vocabulary word, List<Vocabulary> pool,
			java.util.function.Function<Vocabulary, String> text) {
		List<Vocabulary> distractors = new ArrayList<>(MCQ_DISTRACTORS);
		Set<String> taken = new HashSet<>();
		taken.add(text.apply(word));
		// Two passes: same part of speech first, then anything still needed. A text only
		// enters `taken` when actually used, so pass 2 can still pick pass-1 rejects.
		for (boolean samePosOnly : new boolean[] { true, false }) {
			for (Vocabulary candidate : pool) {
				if (distractors.size() == MCQ_DISTRACTORS) {
					break;
				}
				if (candidate.getId().equals(word.getId())
						|| (samePosOnly && !candidate.getPartOfSpeech().equals(word.getPartOfSpeech()))
						|| !taken.add(text.apply(candidate))) {
					continue;
				}
				distractors.add(candidate);
			}
		}

		String correctId = UUID.randomUUID().toString();
		List<Map<String, Object>> entries = new ArrayList<>(distractors.size() + 1);
		entries.add(option(correctId, text.apply(word)));
		for (Vocabulary distractor : distractors) {
			entries.add(option(UUID.randomUUID().toString(), text.apply(distractor)));
		}
		java.util.Collections.shuffle(entries, random);
		return new Options(entries, correctId);
	}

	private Map<String, Object> option(String id, String text) {
		Map<String, Object> entry = new HashMap<>();
		entry.put("id", id);
		entry.put("text", Map.of("en", text, "bn", text));
		return entry;
	}

	private Map<String, Object> mcqKey(Options options, String revealText) {
		Map<String, Object> key = new LinkedHashMap<>();
		key.put("correctOptionId", options.correctId());
		key.put("revealText", revealText);
		return key;
	}
}
