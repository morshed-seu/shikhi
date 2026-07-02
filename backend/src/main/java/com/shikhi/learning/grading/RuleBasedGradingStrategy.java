package com.shikhi.learning.grading;

import com.shikhi.content.domain.ExerciseType;
import com.shikhi.content.domain.HintTrigger;
import com.shikhi.content.web.Bilingual;
import com.shikhi.learning.grading.GradingContext.HintSpec;
import com.shikhi.learning.grading.GradingContext.OptionSpec;
import com.shikhi.platform.error.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Deterministic grading for the pilot exercise types (LLD §5). It grades entirely from the
 * {@link GradingContext} (no I/O) and, on a wrong answer, selects curated feedback by
 * precedence {@code WRONG_ANSWER → PATTERN → DEFAULT}. This is the default strategy and the
 * permanent fallback beneath any future AI grading.
 */
@Component
public class RuleBasedGradingStrategy implements GradingStrategy {

	private static final Bilingual CORRECT_FEEDBACK =
			new Bilingual("Correct!", "সঠিক!");
	private static final Bilingual GENERIC_WRONG =
			new Bilingual("Not quite — try again.", "ঠিক হয়নি — আবার চেষ্টা করুন।");

	@Override
	public boolean supports(ExerciseType type) {
		return switch (type) {
			case MCQ, TYPE_TRANSLATION, FILL_BLANK, WORD_BANK, LISTENING -> true;
			case MATCH -> false; // not in the pilot; add with its own answer shape later
		};
	}

	@Override
	public GradingVerdict grade(GradingContext ctx) {
		boolean correct = switch (ctx.type()) {
			case MCQ, LISTENING -> gradeMcq(ctx);
			case TYPE_TRANSLATION, FILL_BLANK -> gradeText(ctx);
			case WORD_BANK -> gradeWordBank(ctx);
			case MATCH -> throw ApiException.badRequest("UNSUPPORTED_EXERCISE",
					"This exercise type is not gradable yet");
		};
		if (correct) {
			return GradingVerdict.correct(CORRECT_FEEDBACK);
		}
		return selectWrongFeedback(ctx);
	}

	private boolean gradeMcq(GradingContext ctx) {
		String selected = asString(ctx.answer().get("selectedOptionId"));
		return ctx.options().stream()
				.filter(OptionSpec::correct)
				.anyMatch(o -> o.id().toString().equals(selected));
	}

	private boolean gradeText(GradingContext ctx) {
		String submitted = AnswerNormalizer.normalize(asString(ctx.answer().get("text")));
		return ctx.acceptedAnswers().stream()
				.map(AnswerNormalizer::normalize)
				.anyMatch(submitted::equals);
	}

	private boolean gradeWordBank(GradingContext ctx) {
		Object tokens = ctx.answer().get("tokenOrder");
		if (!(tokens instanceof List<?> list)) {
			return false;
		}
		String assembled = AnswerNormalizer.normalize(
				list.stream().map(this::asString).reduce("", (a, b) -> a.isEmpty() ? b : a + " " + b));
		return ctx.acceptedAnswers().stream()
				.map(AnswerNormalizer::normalize)
				.anyMatch(assembled::equals);
	}

	/** Pick the most specific curated hint for a wrong answer; fall back to a generic message. */
	private GradingVerdict selectWrongFeedback(GradingContext ctx) {
		String answerKey = wrongAnswerKey(ctx);

		// 1. WRONG_ANSWER — an exact, curated mistake.
		for (HintSpec hint : hintsOf(ctx, HintTrigger.WRONG_ANSWER)) {
			if (answerKey != null && answerKey.equals(AnswerNormalizer.normalize(hint.triggerKey()))) {
				return GradingVerdict.wrong(toBilingual(hint), null);
			}
		}
		// 2. PATTERN — an L1-transfer tip; report the pattern code.
		List<HintSpec> patternHints = hintsOf(ctx, HintTrigger.PATTERN);
		if (!patternHints.isEmpty()) {
			HintSpec hint = patternHints.get(0);
			return GradingVerdict.wrong(toBilingual(hint), hint.triggerKey());
		}
		// 3. DEFAULT — the fallback hint.
		List<HintSpec> defaultHints = hintsOf(ctx, HintTrigger.DEFAULT);
		if (!defaultHints.isEmpty()) {
			return GradingVerdict.wrong(toBilingual(defaultHints.get(0)), null);
		}
		return GradingVerdict.wrong(GENERIC_WRONG, null);
	}

	/** The normalized key a WRONG_ANSWER hint matches against (text for text types). */
	private String wrongAnswerKey(GradingContext ctx) {
		return switch (ctx.type()) {
			case TYPE_TRANSLATION, FILL_BLANK -> AnswerNormalizer.normalize(asString(ctx.answer().get("text")));
			case MCQ, LISTENING -> asString(ctx.answer().get("selectedOptionId"));
			default -> null;
		};
	}

	private List<HintSpec> hintsOf(GradingContext ctx, HintTrigger trigger) {
		return ctx.hints().stream().filter(h -> h.trigger() == trigger).toList();
	}

	private Bilingual toBilingual(HintSpec hint) {
		return new Bilingual(hint.textEn(), hint.textBn());
	}

	private String asString(Object value) {
		return value == null ? "" : value.toString();
	}
}
