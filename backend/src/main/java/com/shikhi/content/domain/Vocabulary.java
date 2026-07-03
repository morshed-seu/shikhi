package com.shikhi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A single Oxford-3000 headword with its Bengali gloss and a bilingual example. Standalone
 * reference data (not part of the versioned curriculum tree); browsed one CEFR band at a time.
 */
@Entity
@Table(name = "vocabulary")
public class Vocabulary {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(nullable = false)
	private String headword;

	/** Disambiguator for homographs (e.g. bank "money" vs "river"); null in the common case. */
	@Column(name = "sense_label")
	private String senseLabel;

	@Column(name = "part_of_speech", nullable = false)
	private String partOfSpeech;

	@Column(name = "cefr_level", nullable = false)
	private String cefrLevel;

	@Column(name = "bn_gloss", nullable = false)
	private String bnGloss;

	@Column(name = "example_en")
	private String exampleEn;

	@Column(name = "example_bn")
	private String exampleBn;

	@Column(nullable = false)
	private int ordinal;

	protected Vocabulary() {
		// for JPA
	}

	public UUID getId() {
		return id;
	}

	public String getHeadword() {
		return headword;
	}

	public String getSenseLabel() {
		return senseLabel;
	}

	public String getPartOfSpeech() {
		return partOfSpeech;
	}

	public String getCefrLevel() {
		return cefrLevel;
	}

	public String getBnGloss() {
		return bnGloss;
	}

	public String getExampleEn() {
		return exampleEn;
	}

	public String getExampleBn() {
		return exampleBn;
	}

	public int getOrdinal() {
		return ordinal;
	}
}
