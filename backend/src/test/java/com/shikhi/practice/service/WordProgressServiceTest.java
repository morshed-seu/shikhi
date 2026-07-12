package com.shikhi.practice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.practice.config.PlannerProperties;
import com.shikhi.practice.domain.PracticeWordProgress;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.practice.schedule.FixedIntervalScheduler;
import com.shikhi.practice.schedule.ReviewProgress;
import com.shikhi.practice.schedule.ReviewProgressRepository;
import com.shikhi.support.InMemoryJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Every ladder transition from doc 43 §3 (and deviation #7), unit-tested with in-memory
 * repositories and {@link Clock#fixed} — no Spring context, no database, matching
 * {@link PracticeGeneratorTest}'s approach. Default {@link PlannerProperties}: graduation at
 * masteryScore >= 3 / timesCorrect >= 2 / timesSeen >= 3; ladder [0,1,3,7,14,30,...] days.
 */
class WordProgressServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-12T12:00:00Z");

	private final UUID userId = UUID.randomUUID();
	private final UUID vocabularyId = UUID.randomUUID();

	private final InMemoryWordProgressRepository wordProgress =
			new InMemoryWordProgressRepository();
	private final InMemoryReviewProgressRepository reviewProgress =
			new InMemoryReviewProgressRepository();
	private final PlannerProperties planner = new PlannerProperties();
	private final WordProgressService service = new WordProgressService(wordProgress,
			reviewProgress, new FixedIntervalScheduler(planner), planner,
			Clock.fixed(NOW, ZoneOffset.UTC));

	// ---- graduation -------------------------------------------------------------------------

	@Test
	void graduatesExactlyWhenAllThreeThresholdsAreMet() {
		// Unseen words start at mastery 2. After two correct answers mastery is 4 >= 3 and
		// timesCorrect 2 >= 2, but timesSeen is still 2 < 3 — the gate must hold. The third
		// correct answer satisfies all three thresholds at once.
		service.recordAnswer(userId, vocabularyId, true);
		service.recordAnswer(userId, vocabularyId, true);
		assertThat(review()).as("timesSeen below gate — must not graduate").isEmpty();

		service.recordAnswer(userId, vocabularyId, true);

		ReviewProgress created = review().orElseThrow();
		assertThat(created.getReviewStage()).isEqualTo(1);
		assertThat(created.getDueAt()).isEqualTo(NOW.plus(Duration.ofDays(1))); // interval(1)
		assertThat(created.getReviewCount()).isZero(); // graduation is not a review
	}

	@Test
	void doesNotGraduateWhenMasteryIsBelowGateDespiteOtherThresholds() {
		// correct, correct, wrong: timesSeen 3 >= 3, timesCorrect 2 >= 2, but
		// mastery 2 +1 +1 -2 = 2 < 3 — the mastery leg of the gate must hold it back.
		service.recordAnswer(userId, vocabularyId, true);
		service.recordAnswer(userId, vocabularyId, true);
		service.recordAnswer(userId, vocabularyId, false);

		assertThat(review()).isEmpty();
		PracticeWordProgress mastery = mastery().orElseThrow();
		assertThat(mastery.getMasteryScore()).isEqualTo(2);
		assertThat(mastery.getTimesSeen()).isEqualTo(3);
	}

	@Test
	void graduationUsesConfiguredThresholds() {
		planner.setGraduationTimesSeen(1);
		planner.setGraduationTimesCorrect(1);
		planner.setGraduationMastery(3);

		service.recordAnswer(userId, vocabularyId, true); // mastery 3, seen 1, correct 1

		assertThat(review()).isPresent();
	}

	@Test
	void wrongAnswerMeetingTheGateDoesNotGraduateButTheNextCorrectAnswerDoes() {
		// Fix 4: graduation must only ever be evaluated on a correct answer. Configure a
		// trivially low gate so a single WRONG answer numerically satisfies all three
		// thresholds (mastery 2 - 2 = 0 >= 0, timesCorrect 0 >= 0, timesSeen 1 >= 1) — it must
		// still not graduate the word.
		planner.setGraduationMastery(0);
		planner.setGraduationTimesCorrect(0);
		planner.setGraduationTimesSeen(1);

		service.recordAnswer(userId, vocabularyId, false);
		assertThat(review()).as("a wrong answer must never graduate a word, even one that "
				+ "numerically satisfies the gate").isEmpty();

		service.recordAnswer(userId, vocabularyId, true);
		assertThat(review()).as("the next correct answer, still meeting the same gate, "
				+ "graduates normally").isPresent();
	}

	// ---- ladder transitions -----------------------------------------------------------------

	@Test
	void correctWhileDuePromotes() {
		seedReview(2, NOW.minus(Duration.ofHours(1))); // stage 2, due an hour ago
		ReviewProgress seeded = review().orElseThrow();

		service.recordAnswer(userId, vocabularyId, true);

		assertThat(seeded.getReviewStage()).isEqualTo(3);
		assertThat(seeded.getDueAt()).isEqualTo(NOW.plus(Duration.ofDays(7))); // interval(3)
		assertThat(seeded.getLastReviewedAt()).isEqualTo(NOW);
		assertThat(seeded.getReviewCount()).isEqualTo(1);
		assertThat(seeded.getSuccessfulReviews()).isEqualTo(1);
		assertThat(seeded.getFailureStreak()).isZero();
	}

	@Test
	void promoteResetsAPriorFailureStreak() {
		seedReview(3, NOW.minus(Duration.ofDays(1)));
		ReviewProgress seeded = review().orElseThrow();
		seeded.demote(NOW.minus(Duration.ofDays(1)), Duration.ofDays(0)); // streak 1, stage 1, due

		service.recordAnswer(userId, vocabularyId, true);

		assertThat(seeded.getFailureStreak()).isZero();
		assertThat(seeded.getReviewStage()).isEqualTo(2);
	}

	@Test
	void correctWhileNotDueLeavesTheLadderUntouched() {
		Instant futureDue = NOW.plus(Duration.ofDays(5));
		seedReview(3, futureDue);
		ReviewProgress seeded = review().orElseThrow();

		service.recordAnswer(userId, vocabularyId, true);

		// Deviation #7: a non-due appearance (word served via New/Weak bucket) must not
		// inflate the interval — nothing on the ladder moves.
		assertThat(seeded.getReviewStage()).isEqualTo(3);
		assertThat(seeded.getDueAt()).isEqualTo(futureDue);
		assertThat(seeded.getReviewCount()).isZero();
		assertThat(seeded.getSuccessfulReviews()).isZero();
		assertThat(seeded.getLastReviewedAt()).isNull();
		// ...but mastery still records the answer.
		assertThat(mastery().orElseThrow().getTimesCorrect()).isEqualTo(1);
	}

	@Test
	void lateReviewStillPromotesNormally() {
		seedReview(4, NOW.minus(Duration.ofDays(90))); // three months overdue
		ReviewProgress seeded = review().orElseThrow();

		service.recordAnswer(userId, vocabularyId, true);

		// Lateness is never punished: full promotion to stage 5, interval(5) = 30 days.
		assertThat(seeded.getReviewStage()).isEqualTo(5);
		assertThat(seeded.getDueAt()).isEqualTo(NOW.plus(Duration.ofDays(30)));
	}

	@Test
	void wrongAlwaysDemotesEvenWhenNotDue() {
		seedReview(4, NOW.plus(Duration.ofDays(10))); // not due
		ReviewProgress seeded = review().orElseThrow();

		service.recordAnswer(userId, vocabularyId, false);

		assertThat(seeded.getReviewStage()).isEqualTo(2); // 4 - 2
		assertThat(seeded.getDueAt()).isEqualTo(NOW.plus(Duration.ofDays(3))); // interval(2)
		assertThat(seeded.getFailedReviews()).isEqualTo(1);
		assertThat(seeded.getFailureStreak()).isEqualTo(1);
		assertThat(seeded.getLastFailureAt()).isEqualTo(NOW);
	}

	@Test
	void demoteFloorsAtStageZero() {
		seedReview(1, NOW.minus(Duration.ofDays(1)));
		ReviewProgress seeded = review().orElseThrow();

		service.recordAnswer(userId, vocabularyId, false);
		service.recordAnswer(userId, vocabularyId, false);

		assertThat(seeded.getReviewStage()).isZero();
		assertThat(seeded.getDueAt()).isEqualTo(NOW); // interval(0) = 0 days: due immediately
		assertThat(seeded.getFailureStreak()).isEqualTo(2);
		assertThat(seeded.getFailedReviews()).isEqualTo(2);
	}

	// ---- mastery delegation -----------------------------------------------------------------

	@Test
	void masteryUpdateStillDelegatesToPracticeWordProgress() {
		service.recordAnswer(userId, vocabularyId, true);
		service.recordAnswer(userId, vocabularyId, false);

		PracticeWordProgress mastery = mastery().orElseThrow();
		assertThat(mastery.getTimesSeen()).isEqualTo(2);
		assertThat(mastery.getTimesCorrect()).isEqualTo(1);
		assertThat(mastery.getTimesWrong()).isEqualTo(1);
		assertThat(mastery.getMasteryScore()).isEqualTo(1); // 2 + 1 - 2
		assertThat(mastery.getLastWrongAt()).isNotNull();
	}

	// ---- fixtures ---------------------------------------------------------------------------

	private void seedReview(int stage, Instant dueAt) {
		reviewProgress.save(new ReviewProgress(userId, vocabularyId, stage, dueAt));
	}

	private Optional<ReviewProgress> review() {
		return reviewProgress.findById(new ReviewProgress.Key(userId, vocabularyId));
	}

	private Optional<PracticeWordProgress> mastery() {
		return wordProgress.findById(new PracticeWordProgress.Key(userId, vocabularyId));
	}

	private static class InMemoryWordProgressRepository
			extends InMemoryJpaRepository<PracticeWordProgress, PracticeWordProgress.Key>
			implements PracticeWordProgressRepository {

		@Override
		protected PracticeWordProgress.Key idOf(PracticeWordProgress entity) {
			return entity.getKey();
		}

		@Override
		public long countMasteredInBand(UUID userId, String cefrLevel) {
			throw new UnsupportedOperationException("not needed by WordProgressService");
		}
	}

	private static class InMemoryReviewProgressRepository
			extends InMemoryJpaRepository<ReviewProgress, ReviewProgress.Key>
			implements ReviewProgressRepository {

		@Override
		protected ReviewProgress.Key idOf(ReviewProgress entity) {
			return entity.getKey();
		}
	}
}
