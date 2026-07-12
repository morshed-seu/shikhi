package com.shikhi.practice.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.content.domain.Vocabulary;
import com.shikhi.content.repo.VocabularyRepository;
import com.shikhi.identity.domain.Locale;
import com.shikhi.identity.domain.Role;
import com.shikhi.identity.domain.User;
import com.shikhi.identity.repo.UserRepository;
import com.shikhi.practice.config.PlannerProperties;
import com.shikhi.practice.domain.PracticeWordProgress;
import com.shikhi.practice.policy.Bucket;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.practice.schedule.ReviewProgress;
import com.shikhi.practice.schedule.ReviewProgressRepository;
import com.shikhi.progress.service.ProgressService;
import com.shikhi.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@link DailyPlanService} against real Postgres (doc 43 §6 VE3 gate): a fresh learner gets an
 * all-NEW plan at capacity, repeat calls don't duplicate, concurrent first-calls still produce
 * exactly one plan (the idempotency/unique-constraint race path), and a learner with real
 * review/weak state gets all three buckets populated correctly.
 */
class DailyPlanServiceIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	DailyPlanService planService;
	@Autowired
	DailyLearningPlanRepository plans;
	@Autowired
	DailyLearningPlanItemRepository items;
	@Autowired
	UserRepository users;
	@Autowired
	VocabularyRepository vocabulary;
	@Autowired
	PracticeWordProgressRepository wordProgress;
	@Autowired
	ReviewProgressRepository reviewProgress;
	@Autowired
	ProgressService progressService;
	@Autowired
	PlannerProperties planner;

	private UUID createLearner() {
		User user = User.anonymous(Locale.BN);
		user.addRole(Role.LEARNER);
		users.save(user);
		UUID userId = user.getId();
		// Pre-warm user_stats synchronously: ProgressService.getOrCreate's own findOrInsert isn't
		// this milestone's concern, and racing it concurrently below would test that path
		// instead of the plan-header race this test targets.
		progressService.getState(userId);
		return userId;
	}

	@Test
	void freshLearnerAtA1GetsAnAllNewPlanAtCapacity() {
		UUID userId = createLearner();

		DailyLearningPlan plan = planService.getOrCreateToday(userId);

		assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
		assertThat(plan.getPlannedNew()).isEqualTo(planner.getDailyCapacity());
		assertThat(plan.getPlannedWeak()).isZero();
		assertThat(plan.getPlannedReview()).isZero();
		assertThat(plan.getRemainingNew()).isEqualTo(plan.getPlannedNew());

		List<DailyLearningPlanItem> planItems = items.findByPlanIdOrderBySequence(plan.getId());
		assertThat(planItems).hasSize(planner.getDailyCapacity());
		assertThat(planItems).allMatch(i -> i.getBucket() == Bucket.NEW);
		assertThat(planItems).allMatch(i -> i.getStatus() == ItemStatus.PENDING);
		assertThat(planItems).extracting(DailyLearningPlanItem::getSequence)
				.containsExactlyElementsOf(
						IntStream.range(0, planItems.size()).boxed().toList());
	}

	@Test
	void secondCallSameDayReturnsTheSamePlanWithoutDuplicating() {
		UUID userId = createLearner();

		DailyLearningPlan first = planService.getOrCreateToday(userId);
		DailyLearningPlan second = planService.getOrCreateToday(userId);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(plans.findAll().stream().filter(p -> p.getUserId().equals(userId)).count())
				.isEqualTo(1);
	}

	@Test
	void concurrentFirstCallsProduceExactlyOnePlan() throws Exception {
		UUID userId = createLearner();
		int threadCount = 6;
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch go = new CountDownLatch(1);
		List<Future<DailyLearningPlan>> futures = new ArrayList<>();

		try {
			for (int i = 0; i < threadCount; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return planService.getOrCreateToday(userId);
				}));
			}
			ready.await();
			go.countDown();

			Set<UUID> planIds = new HashSet<>();
			for (Future<DailyLearningPlan> future : futures) {
				planIds.add(future.get(30, TimeUnit.SECONDS).getId());
			}

			assertThat(planIds).as("every racing caller must see the same winning plan").hasSize(1);
			assertThat(plans.findAll().stream().filter(p -> p.getUserId().equals(userId)).count())
					.as("exactly one row in daily_learning_plans for this user/day")
					.isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void learnerWithDueReviewAndAWeakWordGetsAllThreeBuckets() {
		UUID userId = createLearner();
		List<Vocabulary> a1Words = vocabulary.findByCefrLevelOrderByOrdinal("A1");
		UUID reviewWordId = a1Words.get(0).getId();
		UUID weakWordId = a1Words.get(1).getId();

		// Graduated word, one day overdue for review.
		PracticeWordProgress graduated = new PracticeWordProgress(userId, reviewWordId);
		graduated.recordAnswer(true, Instant.now());
		graduated.recordAnswer(true, Instant.now());
		graduated.recordAnswer(true, Instant.now());
		wordProgress.save(graduated);
		reviewProgress.save(new ReviewProgress(userId, reviewWordId, 1,
				Instant.now().minus(Duration.ofDays(1))));

		// A weak word: mastery dropped to 0 by a wrong answer.
		PracticeWordProgress weak = new PracticeWordProgress(userId, weakWordId);
		weak.recordAnswer(false, Instant.now());
		wordProgress.save(weak);

		DailyLearningPlan plan = planService.getOrCreateToday(userId);

		assertThat(plan.getPlannedReview()).isEqualTo(1);
		assertThat(plan.getPlannedWeak()).isEqualTo(1);
		assertThat(plan.getPlannedNew()).isEqualTo(planner.getDailyCapacity() - 2);

		List<DailyLearningPlanItem> planItems = items.findByPlanIdOrderBySequence(plan.getId());
		assertThat(planItems).hasSize(planner.getDailyCapacity());

		List<DailyLearningPlanItem> reviewItems = planItems.stream()
				.filter(i -> i.getBucket() == Bucket.REVIEW).toList();
		assertThat(reviewItems).hasSize(1);
		assertThat(reviewItems.get(0).getVocabularyId()).isEqualTo(reviewWordId);

		List<DailyLearningPlanItem> weakItems = planItems.stream()
				.filter(i -> i.getBucket() == Bucket.WEAK).toList();
		assertThat(weakItems).hasSize(1);
		assertThat(weakItems.get(0).getVocabularyId()).isEqualTo(weakWordId);

		// The graduated/review word never doubles into the NEW bucket (V22
		// UNIQUE(plan_id, vocabulary_id) would otherwise reject it outright).
		assertThat(planItems.stream().map(DailyLearningPlanItem::getVocabularyId))
				.doesNotHaveDuplicates();
	}
}
