package com.shikhi.learning.grading;

import com.shikhi.content.domain.ExerciseType;
import com.shikhi.content.domain.HintTrigger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a {@link GradingStrategy} needs to grade one answer, with no dependency on the
 * database — the exercise's correctness data, curated hints, and the learner's answer. Pure
 * input so strategies stay stateless and unit-testable (LLD §2.4).
 *
 * <p>{@code answer} is the raw client payload; its keys depend on the exercise type, e.g.
 * {@code {selectedOptionId}} for MCQ, {@code {text}} for TYPE_TRANSLATION/FILL_BLANK,
 * {@code {tokenOrder: [...]}} for WORD_BANK.
 */
public record GradingContext(UUID exerciseId, ExerciseType type,
		List<String> acceptedAnswers, List<OptionSpec> options, List<String> patternTags,
		List<HintSpec> hints, Map<String, Object> answer) {

	/** An exercise option with its server-side correctness flag. */
	public record OptionSpec(UUID id, boolean correct) {
	}

	/** A curated hint, flattened from the {@code hints} table for grading. */
	public record HintSpec(HintTrigger trigger, String triggerKey, String textEn, String textBn) {
	}
}
