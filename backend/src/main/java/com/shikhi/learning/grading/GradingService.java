package com.shikhi.learning.grading;

import com.shikhi.content.domain.Exercise;
import com.shikhi.content.repo.ExerciseAnswerRepository;
import com.shikhi.content.repo.ExerciseOptionRepository;
import com.shikhi.content.repo.ExerciseRepository;
import com.shikhi.content.repo.HintRepository;
import com.shikhi.learning.grading.GradingContext.HintSpec;
import com.shikhi.learning.grading.GradingContext.OptionSpec;
import com.shikhi.platform.error.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The grading entry point and strategy selector (the D4 seam). It loads an exercise's
 * server-side correctness data and curated hints, builds a {@link GradingContext}, then
 * delegates to the first {@link GradingStrategy} that supports the type. Adding AI grading
 * later means registering a new strategy ahead of the rule-based one — nothing here changes.
 */
@Service
public class GradingService {

	private final List<GradingStrategy> strategies;
	private final ExerciseRepository exercises;
	private final ExerciseOptionRepository options;
	private final ExerciseAnswerRepository answers;
	private final HintRepository hints;

	public GradingService(List<GradingStrategy> strategies, ExerciseRepository exercises,
			ExerciseOptionRepository options, ExerciseAnswerRepository answers,
			HintRepository hints) {
		this.strategies = strategies;
		this.exercises = exercises;
		this.options = options;
		this.answers = answers;
		this.hints = hints;
	}

	@Transactional(readOnly = true)
	public GradingVerdict grade(UUID exerciseId, Map<String, Object> answer) {
		Exercise exercise = exercises.findById(exerciseId)
				.orElseThrow(() -> ApiException.notFound("Exercise not found"));

		List<OptionSpec> optionSpecs = options.findByExerciseIdOrderByOrdinal(exerciseId).stream()
				.map(o -> new OptionSpec(o.getId(), o.isCorrect()))
				.toList();
		List<String> acceptedAnswers = answers.findByExerciseId(exerciseId).stream()
				.map(com.shikhi.content.domain.ExerciseAnswer::getAcceptedAnswer)
				.toList();
		List<HintSpec> hintSpecs = hints.findByExerciseId(exerciseId).stream()
				.map(h -> new HintSpec(h.getTrigger(), h.getTriggerKey(), h.getTextEn(), h.getTextBn()))
				.toList();

		GradingContext ctx = new GradingContext(exerciseId, exercise.getType(), acceptedAnswers,
				optionSpecs, List.of(), hintSpecs, answer);

		GradingStrategy strategy = strategies.stream()
				.filter(s -> s.supports(exercise.getType()))
				.findFirst()
				.orElseThrow(() -> ApiException.badRequest("UNSUPPORTED_EXERCISE",
						"This exercise type is not gradable yet"));
		return strategy.grade(ctx);
	}
}
