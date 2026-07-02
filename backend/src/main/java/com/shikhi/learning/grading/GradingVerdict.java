package com.shikhi.learning.grading;

import com.shikhi.content.web.Bilingual;

/**
 * The result of grading one answer — the stable seam type (ADR-0006, LLD §2.4). Its shape
 * never changes when a new {@link GradingStrategy} (e.g. AI grading) is added, so the
 * learning/progress code that consumes it stays untouched. {@code source} records which
 * strategy produced it ({@code RULE} today; {@code AI} later).
 */
public record GradingVerdict(boolean correct, Bilingual feedback, String matchedPatternCode,
		String source) {

	public static final String SOURCE_RULE = "RULE";

	public static GradingVerdict correct(Bilingual feedback) {
		return new GradingVerdict(true, feedback, null, SOURCE_RULE);
	}

	public static GradingVerdict wrong(Bilingual feedback, String matchedPatternCode) {
		return new GradingVerdict(false, feedback, matchedPatternCode, SOURCE_RULE);
	}
}
