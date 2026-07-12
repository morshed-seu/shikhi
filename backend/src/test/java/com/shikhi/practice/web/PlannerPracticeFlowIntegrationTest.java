package com.shikhi.practice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.plan.DailyLearningPlan;
import com.shikhi.practice.plan.DailyLearningPlanItem;
import com.shikhi.practice.plan.DailyLearningPlanItemRepository;
import com.shikhi.practice.plan.DailyLearningPlanRepository;
import com.shikhi.practice.plan.ItemStatus;
import com.shikhi.practice.plan.PlanStatus;
import com.shikhi.practice.policy.Bucket;
import com.shikhi.practice.repo.PracticeExerciseRepository;
import com.shikhi.practice.repo.PracticeSessionRepository;
import com.shikhi.practice.schedule.ReviewProgress;
import com.shikhi.practice.schedule.ReviewProgressRepository;
import com.shikhi.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/**
 * Plan-backed practice, end-to-end against real Postgres (doc 43 §6 VE4 gate): a fresh
 * learner's round is served entirely from the daily plan, the plan closes out once fully
 * served, free practice/legacy fallback keeps a session going once the plan is exhausted, a
 * word graduates onto the review ladder after repeat correct answers, and the ladder promotes/
 * demotes correctly across simulated days (via {@link MutableClock} — no real sleeping).
 *
 * <p>Runs in its own Spring context ({@code shikhi.practice.planner.enabled=true},
 * {@code daily-capacity=10}) so it never interferes with {@link PracticeFlowIntegrationTest},
 * which asserts the legacy (flag-off) path is untouched in the default context. Shared MockMvc
 * plumbing (token/answer-JSON helpers) lives in {@link AbstractPracticeFlowIntegrationTest}.
 */
@TestPropertySource(properties = {
		"shikhi.practice.planner.enabled=true",
		"shikhi.practice.planner.daily-capacity=10" })
class PlannerPracticeFlowIntegrationTest extends AbstractPracticeFlowIntegrationTest {

	private static final Instant DAY_1 = Instant.parse("2026-07-12T09:00:00Z");

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock testClock() {
			return new MutableClock(DAY_1);
		}
	}

	@Autowired
	PracticeExerciseRepository exercises;
	@Autowired
	PracticeSessionRepository sessions;
	@Autowired
	DailyLearningPlanRepository plans;
	@Autowired
	DailyLearningPlanItemRepository planItems;
	@Autowired
	ReviewProgressRepository reviewProgress;
	@Autowired
	MutableClock clock;

	private String startSession(String auth) throws Exception {
		return mockMvc.perform(post("/v1/practice/sessions")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
	}

	private String nextRound(String auth, UUID sessionId) throws Exception {
		return mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/rounds")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private void complete(String auth, UUID sessionId, String idempotencyKey) throws Exception {
		mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/complete")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + idempotencyKey + "\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void multiDayPlannerJourney() throws Exception {
		String auth = token();

		// ---- Day 1, session 1: round 1 is served entirely from the daily plan -------------
		String round1Body = startSession(auth);
		UUID session1 = UUID.fromString(JsonPath.read(round1Body, "$.sessionId"));
		UUID userId = sessions.findById(session1).orElseThrow().getUserId();

		List<PracticeExercise> round1 = exercises.findBySessionIdAndRoundOrderByOrdinal(session1, 1);
		assertThat(round1).hasSize(10); // ROUND_SIZE == test daily-capacity, by design

		DailyLearningPlan planDay1 = plans.findByUserIdAndPlanDate(userId, LocalDate.now(clock))
				.orElseThrow();
		List<DailyLearningPlanItem> itemsDay1 = planItems.findByPlanIdOrderBySequence(planDay1.getId());
		assertThat(itemsDay1).hasSize(10);
		assertThat(itemsDay1).allMatch(i -> i.getBucket() == Bucket.NEW); // fresh learner: all NEW
		assertThat(itemsDay1).allMatch(i -> i.getStatus() == ItemStatus.COMPLETED);
		assertThat(itemsDay1).allMatch(i -> session1.equals(i.getConsumedSessionId()));

		Set<UUID> round1WordIds = round1.stream().map(PracticeExercise::getVocabularyId)
				.collect(Collectors.toSet());
		assertThat(itemsDay1.stream().map(DailyLearningPlanItem::getVocabularyId)
				.collect(Collectors.toSet())).isEqualTo(round1WordIds);

		// Every plan item consumed: the plan's remaining counters hit zero and it closes out.
		DailyLearningPlan reloadedDay1 = plans.findById(planDay1.getId()).orElseThrow();
		assertThat(reloadedDay1.getRemainingNew()).isZero();
		assertThat(reloadedDay1.getRemainingWeak()).isZero();
		assertThat(reloadedDay1.getRemainingReview()).isZero();
		assertThat(reloadedDay1.getStatus()).isEqualTo(PlanStatus.COMPLETED);

		// ---- Same session, next-round: plan exhausted, and nothing has been answered yet so
		// free practice (previously-SEEN words only) has no candidates either. The composer
		// therefore returns empty and PracticeSessionService falls back to the legacy picker
		// (doc 43 §1.4). This is the actual, deterministic behavior for a truly fresh learner
		// on their very first round; the invariant under test is that the endpoint still
		// returns a healthy round rather than erroring.
		String round2Body = nextRound(auth, session1);
		int round2Size = JsonPath.read(round2Body, "$.exercises.length()");
		assertThat(round2Size).isBetween(1, 10);
		List<PracticeExercise> round2 = exercises.findBySessionIdAndRoundOrderByOrdinal(session1, 2);
		assertThat(round2).hasSize(round2Size);
		Set<UUID> round2WordIds = round2.stream().map(PracticeExercise::getVocabularyId)
				.collect(Collectors.toSet());
		assertThat(round2WordIds).doesNotContainAnyElementsOf(round1WordIds); // no repeats

		// ---- Answer one plan word correctly (1st of 3) while session 1 is still open, then
		// complete the session.
		UUID targetWord = round1WordIds.iterator().next();
		PracticeExercise firstOccurrence = round1.stream()
				.filter(e -> e.getVocabularyId().equals(targetWord)).findFirst().orElseThrow();
		submit(auth, session1, firstOccurrence, correctAnswerJson(firstOccurrence), "grad-1");
		complete(auth, session1, "complete-session-1");

		// ---- Same day, session 2: the plan is fully consumed, so the round comes entirely from
		// free practice — and the target word is now the *only* previously-seen word, so it's
		// the only candidate.
		String round3Body = startSession(auth);
		UUID session2 = UUID.fromString(JsonPath.read(round3Body, "$.sessionId"));
		List<PracticeExercise> round3 = exercises.findBySessionIdAndRoundOrderByOrdinal(session2, 1);
		assertThat(round3).extracting(PracticeExercise::getVocabularyId).containsExactly(targetWord);
		submit(auth, session2, round3.get(0), correctAnswerJson(round3.get(0)), "grad-2");
		complete(auth, session2, "complete-session-2");

		// ---- Same day, session 3: same free-practice pool (still just the target word); this
		// 3rd correct answer crosses the graduation gate (mastery>=3, timesCorrect>=2,
		// timesSeen>=3), and the word enters review_progress at stage 1.
		String round4Body = startSession(auth);
		UUID session3 = UUID.fromString(JsonPath.read(round4Body, "$.sessionId"));
		List<PracticeExercise> round4 = exercises.findBySessionIdAndRoundOrderByOrdinal(session3, 1);
		assertThat(round4).extracting(PracticeExercise::getVocabularyId).containsExactly(targetWord);
		submit(auth, session3, round4.get(0), correctAnswerJson(round4.get(0)), "grad-3");
		complete(auth, session3, "complete-session-3");

		ReviewProgress graduated = reviewProgress
				.findById(new ReviewProgress.Key(userId, targetWord)).orElseThrow();
		assertThat(graduated.getReviewStage()).isEqualTo(1);
		assertThat(graduated.getDueAt()).isEqualTo(DAY_1.plus(Duration.ofDays(1)));

		// ---- Advance one simulated day: the word is now due, so today's brand-new plan must
		// slot it into the REVIEW bucket.
		clock.advance(Duration.ofDays(1));
		Instant day2 = clock.instant();

		String round5Body = startSession(auth);
		UUID session4 = UUID.fromString(JsonPath.read(round5Body, "$.sessionId"));
		DailyLearningPlan planDay2 = plans.findByUserIdAndPlanDate(userId, LocalDate.now(clock))
				.orElseThrow();
		assertThat(planDay2.getId()).isNotEqualTo(planDay1.getId()); // a genuinely new plan

		List<DailyLearningPlanItem> itemsDay2 = planItems.findByPlanIdOrderBySequence(planDay2.getId());
		DailyLearningPlanItem reviewItemDay2 = itemsDay2.stream()
				.filter(i -> i.getVocabularyId().equals(targetWord)).findFirst().orElseThrow();
		assertThat(reviewItemDay2.getBucket()).isEqualTo(Bucket.REVIEW);

		// The plan-capacity (10) matches ROUND_SIZE, so round 1 serves the whole plan again,
		// including the due review word — locate it and answer WRONG.
		List<PracticeExercise> round5 = exercises.findBySessionIdAndRoundOrderByOrdinal(session4, 1);
		PracticeExercise dueExercise = round5.stream()
				.filter(e -> e.getVocabularyId().equals(targetWord)).findFirst().orElseThrow();
		submit(auth, session4, dueExercise, wrongAnswerJson(dueExercise), "demote-1");
		complete(auth, session4, "complete-session-4");

		ReviewProgress demoted = reviewProgress
				.findById(new ReviewProgress.Key(userId, targetWord)).orElseThrow();
		assertThat(demoted.getReviewStage()).isZero(); // max(0, 1 - 2)
		assertThat(demoted.getFailureStreak()).isEqualTo(1);
		assertThat(demoted.getDueAt()).isEqualTo(day2); // interval(0) = immediate

		// ---- Advance one more simulated day: the word resurfaces (still the only, and now
		// most overdue, review candidate). Answering correctly while due promotes it.
		clock.advance(Duration.ofDays(1));
		Instant day3 = clock.instant();

		String round6Body = startSession(auth);
		UUID session5 = UUID.fromString(JsonPath.read(round6Body, "$.sessionId"));
		DailyLearningPlan planDay3 = plans.findByUserIdAndPlanDate(userId, LocalDate.now(clock))
				.orElseThrow();
		List<DailyLearningPlanItem> itemsDay3 = planItems.findByPlanIdOrderBySequence(planDay3.getId());
		assertThat(itemsDay3.stream().filter(i -> i.getVocabularyId().equals(targetWord))
				.findFirst().orElseThrow().getBucket()).isEqualTo(Bucket.REVIEW);

		List<PracticeExercise> round6 = exercises.findBySessionIdAndRoundOrderByOrdinal(session5, 1);
		PracticeExercise dueAgain = round6.stream()
				.filter(e -> e.getVocabularyId().equals(targetWord)).findFirst().orElseThrow();
		submit(auth, session5, dueAgain, correctAnswerJson(dueAgain), "promote-1");
		complete(auth, session5, "complete-session-5");

		ReviewProgress promoted = reviewProgress
				.findById(new ReviewProgress.Key(userId, targetWord)).orElseThrow();
		assertThat(promoted.getReviewStage()).isEqualTo(1); // 0 + 1
		assertThat(promoted.getFailureStreak()).isZero();
		assertThat(promoted.getDueAt()).isEqualTo(day3.plus(Duration.ofDays(1)));
	}
}
