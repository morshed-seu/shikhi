package com.shikhi.learning.web;

import com.shikhi.content.web.Bilingual;
import com.shikhi.learning.grading.GradingVerdict;

/** Contract {@code Verdict} — the client-facing view of a {@link GradingVerdict}. */
public record Verdict(boolean correct, Bilingual feedback, String matchedPatternCode,
		String source) {

	public static Verdict from(GradingVerdict verdict) {
		return new Verdict(verdict.correct(), verdict.feedback(), verdict.matchedPatternCode(),
				verdict.source());
	}
}
