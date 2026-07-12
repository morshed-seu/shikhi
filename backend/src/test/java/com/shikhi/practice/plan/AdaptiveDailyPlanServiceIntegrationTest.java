package com.shikhi.practice.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.content.domain.Vocabulary;
import com.shikhi.content.repo.VocabularyRepository;
import com.shikhi.identity.domain.Locale;
import com.shikhi.identity.domain.Role;
import com.shikhi.identity.domain.User;
import com.shikhi.identity.repo.UserRepository;
import com.shikhi.practice.config.PlannerProperties;
import com.shikhi.practice.domain.PracticeAnswer;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.domain.PracticeExerciseType;
import com.shikhi.practice.domain.PracticeSession;
import com.shikhi.practice.domain.PracticeWordProgress;
import com.shikhi.practice.repo.PracticeAnswerRepository;
import com.shikhi.practice.repo.PracticeExerciseRepository;
import com.shikhi.practice.repo.PracticeSessionRepository;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.progress.service.ProgressService;
import com.shikhi.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * {@link DailyPlanService}'s adaptive accuracy policy (doc 42 §6.4, doc 43 §6 VE5 gate) against
 * real Postgres. Runs in its own Spring context with a small {@code daily-capacity=20} so a
 * repair-mode plan's weak-heavy composition can be demonstrated without seeding hundreds of
 * weak candidates — {@link DailyPlanServiceIntegrationTest} covers the default-capacity,
 * default-config-adaptive-policy-inert scenarios instead.
 */
@TestPropertySource(properties = "shikhi.practice.planner.daily-capacity=20")
class AdaptiveDailyPlanServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	DailyPlanService planService;
	@Autowired
	DailyLearningPlanItemRepository items;
	@Autowired
	UserRepository users;
	@Autowired
	VocabularyRepository vocabulary;
	@Autowired
	PracticeWordProgressRepository wordProgress;
	@Autowired
	PracticeSessionRepository sessions;
	@Autowired
	PracticeExerciseRepository exercises;
	@Autowired
	PracticeAnswerRepository answers;
	@Autowired
	ProgressService progressService;
	@Autowired
	PlannerProperties planner;

	private UUID createLearner() {
		User user = User.anonymous(Locale.BN);
		user.addRole(Role.LEARNER);
		users.save(user);
		UUID userId = user.getId();
		progressService.getState(userId);
		return userId;
	}

	/** One session + one exercise (reused across every answer) with {@code correctAnswers}
	 * correct out of {@code totalAnswers}, all "now" so they land inside the rolling window. */
	private void seedAnswerHistory(UUID userId, UUID exerciseWordId, int totalAnswers,
			int correctAnswers) {
		PracticeSession session = new PracticeSession(userId, "A1");
		sessions.save(session);
		PracticeExercise exercise = new PracticeExercise(session.getId(), 1, 1, exerciseWordId,
				PracticeExerciseType.WORD_MEANING, "prompt", "prompt-bn",
				Map.of("options", List.of()), Map.of("correctOptionId", UUID.randomUUID().toString()));
		exercises.save(exercise);

		for (int i = 0; i < totalAnswers; i++) {
			boolean correct = i < correctAnswers;
			answers.save(new PracticeAnswer(session.getId(), userId, exercise.getId(),
					"seed-" + userId + "-" + i, correct));
		}
	}

	private void seedWeakWords(UUID userId, List<UUID> wordIds) {
		for (UUID wordId : wordIds) {
			PracticeWordProgress weak = new PracticeWordProgress(userId, wordId);
			weak.recordAnswer(false, Instant.now()); // masteryScore 2 -> 0, at/below the default weak threshold
			wordProgress.save(weak);
		}
	}

	@Test
	void noAnswerHistoryIsColdStartAndKeepsTheConfiguredBaseSplit() {
		UUID userId = createLearner();

		DailyLearningPlan plan = planService.getOrCreateToday(userId);

		// Cold start (sampleSize 0 < AdaptiveAllocationPolicy.MIN_SAMPLE_SIZE): no weak/review
		// candidates exist either, so redistribution still lands everything in NEW — the point
		// of this test is the *config snapshot*, not the bucket split.
		assertThat(plan.getPlannedNew()).isEqualTo(planner.getDailyCapacity());
		assertThat(plan.getPlannedWeak()).isZero();
		assertThat(plan.getPlannedReview()).isZero();

		Map<String, Object> snapshot = plan.getConfigSnapshot();
		assertThat(snapshot.get("rollingAccuracy")).isEqualTo(0.0);
		assertThat(snapshot.get("accuracySampleSize")).isEqualTo(0);
		assertThat(snapshot.get("adjustedNewPercent")).isEqualTo(planner.getNewPercent());
		assertThat(snapshot.get("adjustedWeakPercent")).isEqualTo(planner.getWeakPercent());
		assertThat(snapshot.get("adjustedReviewPercent")).isEqualTo(planner.getReviewPercent());
	}

	@Test
	void sub60PercentRollingAccuracyProducesARepairModeWeakHeavyPlan() {
		UUID userId = createLearner();
		List<Vocabulary> a1Words = vocabulary.findByCefrLevelOrderByOrdinal("A1");
		UUID exerciseWordId = a1Words.get(0).getId();
		List<UUID> weakWordIds = a1Words.subList(1, 21).stream().map(Vocabulary::getId).toList();

		// 4/20 = 20% rolling accuracy, well under the 60% repair-mode boundary, with exactly
		// AdaptiveAllocationPolicy.MIN_SAMPLE_SIZE answers so cold start does not apply.
		seedAnswerHistory(userId, exerciseWordId, 20, 4);
		seedWeakWords(userId, weakWordIds);

		DailyLearningPlan plan = planService.getOrCreateToday(userId);

		// Repair-mode percents (5/80/15) applied to capacity 20, then redistributed: 0 due
		// reviews donate their whole 3-slot target (70% to weak, 30% to new) before weak/new
		// candidate availability is checked. See AllocationPolicyTest for the same cascade
		// mechanics in isolation.
		assertThat(plan.getPlannedReview()).isZero();
		assertThat(plan.getPlannedWeak()).isEqualTo(18);
		assertThat(plan.getPlannedNew()).isEqualTo(2);
		assertThat(plan.getPlannedNew()).isLessThan(planner.getDailyCapacity() * planner
				.getNewPercent() / 100); // far below the un-adapted 60% new target

		List<DailyLearningPlanItem> planItems = items.findByPlanIdOrderBySequence(plan.getId());
		assertThat(planItems).hasSize(planner.getDailyCapacity());

		Map<String, Object> snapshot = plan.getConfigSnapshot();
		assertThat((Double) snapshot.get("rollingAccuracy")).isCloseTo(0.2, org.assertj.core.data
				.Offset.offset(1e-9));
		assertThat(snapshot.get("accuracySampleSize")).isEqualTo(20);
		assertThat(snapshot.get("adjustedNewPercent")).isEqualTo(5);
		assertThat(snapshot.get("adjustedWeakPercent")).isEqualTo(80);
		assertThat(snapshot.get("adjustedReviewPercent")).isEqualTo(15);
	}
}
