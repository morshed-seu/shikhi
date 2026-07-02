package com.shikhi.content.domain;

/**
 * When a curated hint applies. Grading picks feedback by precedence (LLD §5):
 * {@code WRONG_ANSWER} (exact match on a known mistake) → {@code PATTERN} (an L1-transfer
 * tag) → {@code DEFAULT} (the fallback hint).
 */
public enum HintTrigger {
	DEFAULT,
	PATTERN,
	WRONG_ANSWER
}
