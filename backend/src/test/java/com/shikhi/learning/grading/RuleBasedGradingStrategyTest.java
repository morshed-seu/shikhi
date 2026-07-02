package com.shikhi.learning.grading;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.content.domain.ExerciseType;
import com.shikhi.content.domain.HintTrigger;
import com.shikhi.learning.grading.GradingContext.HintSpec;
import com.shikhi.learning.grading.GradingContext.OptionSpec;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for the deterministic grading rules (LLD §5) and hint precedence. No
 * database — the strategy grades entirely from a hand-built {@link GradingContext}.
 */
class RuleBasedGradingStrategyTest {

	private final RuleBasedGradingStrategy strategy = new RuleBasedGradingStrategy();

	private static final UUID CORRECT_OPT = UUID.randomUUID();
	private static final UUID WRONG_OPT = UUID.randomUUID();

	private final List<HintSpec> translationHints = List.of(
			new HintSpec(HintTrigger.WRONG_ANSWER, "i fine", "Need the verb am.", "am লাগবে।"),
			new HintSpec(HintTrigger.PATTERN, "COPULA", "Bengali drops to-be.", "বাংলায় বাদ পড়ে।"),
			new HintSpec(HintTrigger.DEFAULT, null, "Check the verb.", "ক্রিয়া দেখুন।"));

	private GradingContext mcq(String selectedOptionId) {
		return new GradingContext(UUID.randomUUID(), ExerciseType.MCQ, List.of(),
				List.of(new OptionSpec(CORRECT_OPT, true), new OptionSpec(WRONG_OPT, false)),
				List.of(), List.of(), Map.of("selectedOptionId", selectedOptionId));
	}

	private GradingContext translation(String text, List<HintSpec> hints) {
		return new GradingContext(UUID.randomUUID(), ExerciseType.TYPE_TRANSLATION,
				List.of("I am fine", "I am well"), List.of(), List.of(), hints,
				Map.of("text", text));
	}

	@Test
	void mcqCorrectWhenSelectedOptionIsTheCorrectOne() {
		assertThat(strategy.grade(mcq(CORRECT_OPT.toString())).correct()).isTrue();
	}

	@Test
	void mcqWrongWhenSelectedOptionIsIncorrect() {
		assertThat(strategy.grade(mcq(WRONG_OPT.toString())).correct()).isFalse();
	}

	@Test
	void translationAcceptsExactAndNormalizedAndVariantAnswers() {
		assertThat(strategy.grade(translation("I am fine", List.of())).correct()).isTrue();
		// trimmed, case-folded, trailing punctuation stripped, whitespace collapsed
		assertThat(strategy.grade(translation("  i  AM   fine. ", List.of())).correct()).isTrue();
		// a curated accepted variant
		assertThat(strategy.grade(translation("I am well", List.of())).correct()).isTrue();
	}

	@Test
	void wrongAnswerHintTakesPrecedenceOverPatternAndDefault() {
		GradingVerdict verdict = strategy.grade(translation("I fine", translationHints));
		assertThat(verdict.correct()).isFalse();
		assertThat(verdict.feedback().en()).isEqualTo("Need the verb am.");
		assertThat(verdict.matchedPatternCode()).isNull();
	}

	@Test
	void patternHintUsedForOtherWrongAnswersAndReportsPatternCode() {
		GradingVerdict verdict = strategy.grade(translation("I good", translationHints));
		assertThat(verdict.correct()).isFalse();
		assertThat(verdict.feedback().bn()).isEqualTo("বাংলায় বাদ পড়ে।");
		assertThat(verdict.matchedPatternCode()).isEqualTo("COPULA");
	}

	@Test
	void defaultHintUsedWhenNoWrongAnswerOrPatternHintMatches() {
		List<HintSpec> onlyDefault = List.of(
				new HintSpec(HintTrigger.DEFAULT, null, "Check the verb.", "ক্রিয়া দেখুন।"));
		GradingVerdict verdict = strategy.grade(translation("I good", onlyDefault));
		assertThat(verdict.feedback().en()).isEqualTo("Check the verb.");
		assertThat(verdict.matchedPatternCode()).isNull();
	}

	@Test
	void genericFeedbackWhenNoHintsCurated() {
		GradingVerdict verdict = strategy.grade(translation("I good", List.of()));
		assertThat(verdict.correct()).isFalse();
		assertThat(verdict.feedback().en()).isNotBlank();
	}

	@Test
	void wordBankChecksAssembledTokenOrder() {
		GradingContext base = new GradingContext(UUID.randomUUID(), ExerciseType.WORD_BANK,
				List.of("I am fine"), List.of(), List.of(), List.of(),
				Map.of("tokenOrder", List.of("I", "am", "fine")));
		assertThat(strategy.grade(base).correct()).isTrue();

		GradingContext wrongOrder = new GradingContext(UUID.randomUUID(), ExerciseType.WORD_BANK,
				List.of("I am fine"), List.of(), List.of(), List.of(),
				Map.of("tokenOrder", List.of("fine", "am", "I")));
		assertThat(strategy.grade(wrongOrder).correct()).isFalse();
	}

	@Test
	void matchTypeIsNotYetSupported() {
		assertThat(strategy.supports(ExerciseType.MATCH)).isFalse();
		assertThat(strategy.supports(ExerciseType.MCQ)).isTrue();
	}
}
