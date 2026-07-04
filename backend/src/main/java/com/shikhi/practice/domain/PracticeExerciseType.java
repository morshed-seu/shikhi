package com.shikhi.practice.domain;

/**
 * Generated-exercise formats (E12, PRD US-12.5). Word-level formats drill one headword;
 * sentence-level formats use the word's short bilingual example so sentences stay small.
 */
public enum PracticeExerciseType {

	/** EN headword → pick the Bengali gloss (MCQ). */
	WORD_MEANING(false),
	/** Bengali gloss → pick the EN headword (MCQ). */
	MEANING_WORD(false),
	/** Example sentence with the headword blanked → pick the missing word (MCQ). */
	SENTENCE_GAP(true),
	/** Arrange word tiles to rebuild the short EN example sentence (word bank). */
	SENTENCE_BUILD(true),
	/** Bengali gloss → type the EN headword. */
	TYPE_WORD(false);

	private final boolean sentenceLevel;

	PracticeExerciseType(boolean sentenceLevel) {
		this.sentenceLevel = sentenceLevel;
	}

	public boolean isSentenceLevel() {
		return sentenceLevel;
	}
}
