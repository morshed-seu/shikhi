package com.shikhi.progress.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.shikhi.practice.config.PlannerProperties;
import com.shikhi.practice.domain.PracticeWordProgress;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.practice.schedule.FixedIntervalScheduler;
import com.shikhi.practice.schedule.ReviewProgress;
import com.shikhi.practice.schedule.ReviewProgressRepository;
import com.shikhi.practice.service.WordProgressService;
import com.shikhi.progress.domain.ProcessedEvent;
import com.shikhi.progress.domain.UserStats;
import com.shikhi.progress.repo.ProcessedEventRepository;
import com.shikhi.progress.web.Stats;
import com.shikhi.progress.web.SyncEvent;
import com.shikhi.support.InMemoryJpaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ProgressEventApplier}'s {@code "PRACTICE_ANSWER"} routing (OF5, doc 93 §5): it must
 * award XP/hearts via {@link ProgressService#recordPracticeAnswer} AND advance mastery/
 * review-ladder state via {@link WordProgressService#recordAnswer} — both, not just one — and
 * must not be confused with the pre-existing {@code "ANSWER"}/{@code "COMPLETE_LESSON"} cases,
 * which stay untouched. No Mockito on the test classpath (see {@code InMemoryJpaRepository}):
 * {@link ProgressService} is faked by subclassing (it has no interface and heavier
 * transaction-manager dependencies unit tests shouldn't need to stand up), while
 * {@link WordProgressService} is exercised for real, backed by in-memory repositories and a
 * fixed {@link Clock} — the same approach {@code WordProgressServiceTest} uses.
 */
class ProgressEventApplierTest {

	private static final Instant CLOCK_NOW = Instant.parse("2026-07-18T12:00:00Z");

	private final UUID userId = UUID.randomUUID();
	private final UUID vocabularyId = UUID.randomUUID();

	private final FakeProcessedEventRepository processed = new FakeProcessedEventRepository();
	private final FakeProgressService progressService = new FakeProgressService();
	private final InMemoryWordProgressRepository wordProgress = new InMemoryWordProgressRepository();
	private final InMemoryReviewProgressRepository reviewProgress =
			new InMemoryReviewProgressRepository();
	private final PlannerProperties planner = new PlannerProperties();
	private final WordProgressService wordProgressService = new WordProgressService(wordProgress,
			reviewProgress, new FixedIntervalScheduler(planner), planner,
			Clock.fixed(CLOCK_NOW, ZoneOffset.UTC));

	private final ProgressEventApplier applier =
			new ProgressEventApplier(processed, progressService, wordProgressService);

	// ---- correct / incorrect routing ---------------------------------------------------------

	@Test
	void correctPracticeAnswerAwardsXpAndStrengthensMastery() {
		apply("PRACTICE_ANSWER", Map.of(
				"vocabularyId", vocabularyId.toString(),
				"correct", true,
				"answeredAt", CLOCK_NOW.toString()));

		assertThat(progressService.practiceAnswerCalls).containsExactly(new Object[] { userId, true });

		PracticeWordProgress mastery = mastery().orElseThrow();
		assertThat(mastery.getMasteryScore()).isEqualTo(PracticeWordProgress.UNSEEN_MASTERY + 1);
		assertThat(mastery.getTimesCorrect()).isEqualTo(1);
	}

	@Test
	void incorrectPracticeAnswerLosesAHeartAndWeakensMastery() {
		apply("PRACTICE_ANSWER", Map.of(
				"vocabularyId", vocabularyId.toString(),
				"correct", false,
				"answeredAt", CLOCK_NOW.toString()));

		assertThat(progressService.practiceAnswerCalls).containsExactly(new Object[] { userId, false });

		PracticeWordProgress mastery = mastery().orElseThrow();
		assertThat(mastery.getMasteryScore()).isEqualTo(PracticeWordProgress.UNSEEN_MASTERY - 2);
		assertThat(mastery.getTimesWrong()).isEqualTo(1);
	}

	@Test
	void bothProgressServiceAndWordProgressServiceAreCalledNotJustOne() {
		// Three corrects graduate the word (default thresholds: mastery>=3, timesCorrect>=2,
		// timesSeen>=3) — proves recordAnswer (not just recordPracticeAnswer) actually ran, since
		// a review-ladder row only appears via WordProgressService's own graduation logic.
		apply("PRACTICE_ANSWER", correctPayload());
		apply("PRACTICE_ANSWER", correctPayload());
		apply("PRACTICE_ANSWER", correctPayload());

		assertThat(progressService.practiceAnswerCalls).hasSize(3);
		assertThat(review()).as("WordProgressService.recordAnswer must have graduated the word")
				.isPresent();
	}

	// ---- malformed payload ---------------------------------------------------------------------

	@Test
	void missingVocabularyIdIsSkippedWithoutThrowing() {
		assertThatCode(() -> apply("PRACTICE_ANSWER", Map.of("correct", true)))
				.doesNotThrowAnyException();

		assertThat(progressService.practiceAnswerCalls).isEmpty();
		assertThat(mastery()).isEmpty();
	}

	@Test
	void invalidVocabularyIdIsSkippedWithoutThrowing() {
		assertThatCode(() -> apply("PRACTICE_ANSWER",
				Map.of("vocabularyId", "not-a-uuid", "correct", true)))
				.doesNotThrowAnyException();

		assertThat(progressService.practiceAnswerCalls).isEmpty();
	}

	// ---- answeredAt handling --------------------------------------------------------------------

	@Test
	void missingAnsweredAtFallsBackToTheClock() {
		apply("PRACTICE_ANSWER", Map.of("vocabularyId", vocabularyId.toString(), "correct", true));

		assertThat(mastery().orElseThrow().getLastSeenAt()).isEqualTo(CLOCK_NOW);
	}

	@Test
	void malformedAnsweredAtFallsBackToTheClockInsteadOfCrashing() {
		assertThatCode(() -> apply("PRACTICE_ANSWER", Map.of(
				"vocabularyId", vocabularyId.toString(),
				"correct", true,
				"answeredAt", "not-an-instant")))
				.doesNotThrowAnyException();

		assertThat(mastery().orElseThrow().getLastSeenAt()).isEqualTo(CLOCK_NOW);
	}

	@Test
	void presentValidAnsweredAtIsParsedAndUsedInsteadOfTheClock() {
		Instant threeDaysAgo = CLOCK_NOW.minus(Duration.ofDays(3));

		apply("PRACTICE_ANSWER", Map.of(
				"vocabularyId", vocabularyId.toString(),
				"correct", true,
				"answeredAt", threeDaysAgo.toString()));

		// Not clock.instant() — the explicit, older client timestamp was actually used.
		assertThat(mastery().orElseThrow().getLastSeenAt()).isEqualTo(threeDaysAgo);
	}

	@Test
	void answeredAtPropagatesIntoTheReviewLadderDueDateOnGraduation() {
		Instant answeredAt = CLOCK_NOW.minus(Duration.ofDays(5));
		Map<String, Object> payload = Map.of(
				"vocabularyId", vocabularyId.toString(),
				"correct", true,
				"answeredAt", answeredAt.toString());

		apply("PRACTICE_ANSWER", payload);
		apply("PRACTICE_ANSWER", payload);
		apply("PRACTICE_ANSWER", payload);

		ReviewProgress graduated = review().orElseThrow();
		// Graduation stamps dueAt as answeredAt + interval(1), not clock.instant() + interval(1).
		assertThat(graduated.getDueAt()).isEqualTo(answeredAt.plus(Duration.ofDays(1)));
	}

	// ---- SET_LEVEL / last-write-wins (UO1) ---------------------------------------------------

	@Test
	void setLevelEventAppliesANewLevel() {
		apply("SET_LEVEL", Map.of("cefrLevel", "B2", "changedAt", CLOCK_NOW.toString()));

		assertThat(progressService.currentLevel(userId)).isEqualTo("B2");
	}

	@Test
	void setLevelEventWithAnOlderChangedAtThanStoredIsRejectedByLastWriteWins() {
		Instant older = CLOCK_NOW.minus(Duration.ofDays(2));

		apply("SET_LEVEL", Map.of("cefrLevel", "B2", "changedAt", CLOCK_NOW.toString()));
		apply("SET_LEVEL", Map.of("cefrLevel", "A2", "changedAt", older.toString()));

		assertThat(progressService.currentLevel(userId)).isEqualTo("B2");
	}

	@Test
	void idempotentReplayOfASetLevelEventIsANoOp() {
		SyncEvent event = new SyncEvent("set-level-1", "SET_LEVEL",
				Map.of("cefrLevel", "B2", "changedAt", CLOCK_NOW.toString()));

		applier.applyIfNew(userId, event);
		applier.applyIfNew(userId, event);

		assertThat(progressService.setLevelCalls).hasSize(1);
	}

	// ---- fixtures -------------------------------------------------------------------------------

	private Map<String, Object> correctPayload() {
		return Map.of("vocabularyId", vocabularyId.toString(), "correct", true,
				"answeredAt", CLOCK_NOW.toString());
	}

	/** Every call mints its own idempotency key, so repeated calls are distinct events. */
	private void apply(String type, Map<String, Object> payload) {
		applier.applyIfNew(userId, new SyncEvent(UUID.randomUUID().toString(), type, payload));
	}

	private Optional<PracticeWordProgress> mastery() {
		return wordProgress.findById(new PracticeWordProgress.Key(userId, vocabularyId));
	}

	private Optional<ReviewProgress> review() {
		return reviewProgress.findById(new ReviewProgress.Key(userId, vocabularyId));
	}

	// ---- fakes ------------------------------------------------------------------------------

	/**
	 * Subclass-based spy: {@link ProgressService} has no interface and a constructor pulling in
	 * repositories/a transaction manager this unit test has no reason to stand up. Overriding
	 * just the one method {@link ProgressEventApplier} calls for {@code PRACTICE_ANSWER} keeps
	 * the fake honest — everything else would throw a NPE if accidentally exercised.
	 */
	private static class FakeProgressService extends ProgressService {

		final List<Object[]> practiceAnswerCalls = new ArrayList<>();
		final List<Object[]> setLevelCalls = new ArrayList<>();
		private final Map<UUID, UserStats> statsByUser = new HashMap<>();

		FakeProgressService() {
			super(null, null, null, null, null, null, null);
		}

		@Override
		public Stats recordPracticeAnswer(UUID userId, boolean correct) {
			practiceAnswerCalls.add(new Object[] { userId, correct });
			return new Stats(0, 0, 0, 0, 5, 10, "A1", Map.of());
		}

		@Override
		public Stats setLevel(UUID userId, String cefrLevel, Instant changedAt) {
			setLevelCalls.add(new Object[] { userId, cefrLevel, changedAt });
			UserStats s = statsByUser.computeIfAbsent(userId, UserStats::new);
			s.setCefrLevel(cefrLevel, changedAt);
			return new Stats(0, 0, 0, 0, 5, 10, s.getCefrLevel(), Map.of());
		}

		String currentLevel(UUID userId) {
			return statsByUser.get(userId).getCefrLevel();
		}
	}

	private static class FakeProcessedEventRepository
			extends InMemoryJpaRepository<ProcessedEvent, UUID>
			implements ProcessedEventRepository {

		@Override
		protected UUID idOf(ProcessedEvent entity) {
			return UUID.randomUUID();
		}

		@Override
		public boolean existsByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
			return findAll().stream().anyMatch(e -> e.getUserId().equals(userId)
					&& e.getIdempotencyKey().equals(idempotencyKey));
		}
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
			throw new UnsupportedOperationException("not needed by this test");
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
