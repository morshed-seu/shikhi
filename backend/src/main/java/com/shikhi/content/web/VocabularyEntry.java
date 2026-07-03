package com.shikhi.content.web;

import com.shikhi.content.domain.Vocabulary;
import java.util.UUID;

/** A dictionary entry for the word browser ({@code /v1/vocabulary}). */
public record VocabularyEntry(UUID id, String headword, String senseLabel, String partOfSpeech,
		String cefrLevel, String bnGloss, String exampleEn, String exampleBn) {

	public static VocabularyEntry from(Vocabulary v) {
		return new VocabularyEntry(v.getId(), v.getHeadword(), v.getSenseLabel(),
				v.getPartOfSpeech(), v.getCefrLevel(), v.getBnGloss(), v.getExampleEn(),
				v.getExampleBn());
	}
}
