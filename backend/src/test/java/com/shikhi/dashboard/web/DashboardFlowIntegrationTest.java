package com.shikhi.dashboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shikhi.content.domain.ContentStatus;
import com.shikhi.content.domain.ContentVersion;
import com.shikhi.content.domain.Exercise;
import com.shikhi.content.domain.ExerciseOption;
import com.shikhi.content.domain.ExerciseType;
import com.shikhi.content.repo.ContentVersionRepository;
import com.shikhi.content.repo.ExerciseOptionRepository;
import com.shikhi.content.repo.ExerciseRepository;
import com.shikhi.content.repo.LessonRepository;
import com.shikhi.content.repo.LevelRepository;
import com.shikhi.content.repo.UnitRepository;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.repo.PracticeExerciseRepository;
import com.shikhi.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Dashboard end-to-end against real Postgres (E13, MD1): a fresh learner's all-zero snapshot
 * with the five seeded CEFR bands, deltas driven by a real practice session and a real lesson
 * session, the read-only invariant, and the auth gate.
 */
@AutoConfigureMockMvc
class DashboardFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;
	@Autowired
	PracticeExerciseRepository practiceExercises;
	@Autowired
	ContentVersionRepository versions;
	@Autowired
	LevelRepository levels;
	@Autowired
	UnitRepository units;
	@Autowired
	LessonRepository lessons;
	@Autowired
	ExerciseRepository exercises;
	@Autowired
	ExerciseOptionRepository options;
	@Autowired
	JdbcTemplate jdbc;

	/** Register a real learner (with the given email) and return their bearer token. */
	private String register(String email) throws Exception {
		String body = mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}

	/** Register a real learner and return their bearer token. */
	private String token() throws Exception {
		return register("dashboarder-" + UUID.randomUUID() + "@example.com");
	}

	private String getDashboard(String auth) throws Exception {
		return mockMvc.perform(get("/v1/dashboard").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	@Test
	void freshLearnerSeesAllZeroTotalsAndFiveSeededBands() throws Exception {
		String auth = token();

		mockMvc.perform(get("/v1/dashboard").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewDueCount").value(0))
				.andExpect(jsonPath("$.lessonsCompleted").value(0))
				.andExpect(jsonPath("$.practiceSessionsCompleted").value(0))
				.andExpect(jsonPath("$.totalAnswered").value(0))
				.andExpect(jsonPath("$.totalCorrect").value(0))
				.andExpect(jsonPath("$.wordMastery.length()").value(5))
				.andExpect(jsonPath("$.wordMastery[0].cefrLevel").value("A1"))
				.andExpect(jsonPath("$.wordMastery[0].mastered").value(0))
				.andExpect(jsonPath("$.wordMastery[0].total").value(899))
				.andExpect(jsonPath("$.wordMastery[1].cefrLevel").value("A2"))
				.andExpect(jsonPath("$.wordMastery[1].mastered").value(0))
				.andExpect(jsonPath("$.wordMastery[1].total").value(801))
				.andExpect(jsonPath("$.wordMastery[2].cefrLevel").value("B1"))
				.andExpect(jsonPath("$.wordMastery[2].mastered").value(0))
				.andExpect(jsonPath("$.wordMastery[2].total").value(700))
				.andExpect(jsonPath("$.wordMastery[3].cefrLevel").value("B2"))
				.andExpect(jsonPath("$.wordMastery[3].mastered").value(0))
				.andExpect(jsonPath("$.wordMastery[3].total").value(1300))
				.andExpect(jsonPath("$.wordMastery[4].cefrLevel").value("C1"))
				.andExpect(jsonPath("$.wordMastery[4].mastered").value(0))
				.andExpect(jsonPath("$.wordMastery[4].total").value(1311));
	}

	@Test
	void unauthenticatedRequestIsRejected() throws Exception {
		mockMvc.perform(get("/v1/dashboard")).andExpect(status().isUnauthorized());
	}

	@Test
	void readingTheDashboardNeverMutatesState() throws Exception {
		String auth = token();

		String statsBefore = mockMvc.perform(get("/v1/stats").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		String first = getDashboard(auth);
		String second = getDashboard(auth);
		assertThat(second).isEqualTo(first);

		String statsAfter = mockMvc.perform(get("/v1/stats").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(statsAfter).isEqualTo(statsBefore);
	}

	/**
	 * The strictest read-only proof: when the VERY FIRST authenticated request of a fresh
	 * account is the dashboard, the lazily-created {@code UserStats} inside {@code
	 * ProgressService.getState} must never reach the database — the read-only transaction's
	 * suppressed flush is what keeps {@code GET /dashboard} write-free (LLD §2.9). Asserted
	 * directly against {@code user_stats} so a future propagation/open-in-view change that
	 * silently starts committing the row fails this test.
	 */
	@Test
	void dashboardAsVeryFirstRequestWritesNoUserStatsRow() throws Exception {
		String email = "first-touch-" + UUID.randomUUID() + "@example.com";
		String auth = register(email);

		// No other authenticated call before this — the dashboard is the account's first touch.
		mockMvc.perform(get("/v1/dashboard").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stats.hearts").value(5))
				.andExpect(jsonPath("$.totalAnswered").value(0));

		Long statsRows = jdbc.queryForObject("""
				select count(*) from user_stats us
				join identities i on i.user_id = us.user_id
				where i.external_ref = ?
				""", Long.class, email);
		assertThat(statsRows).isZero();
	}

	/** Guests get the same zero-state dashboard as registered learners (US-13.1). */
	@Test
	void guestSeesZeroStateDashboard() throws Exception {
		String body = mockMvc.perform(post("/v1/auth/guest")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"uiLocale\":\"bn\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String auth = "Bearer " + JsonPath.read(body, "$.accessToken");

		mockMvc.perform(get("/v1/dashboard").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewDueCount").value(0))
				.andExpect(jsonPath("$.lessonsCompleted").value(0))
				.andExpect(jsonPath("$.practiceSessionsCompleted").value(0))
				.andExpect(jsonPath("$.totalAnswered").value(0))
				.andExpect(jsonPath("$.totalCorrect").value(0))
				.andExpect(jsonPath("$.wordMastery.length()").value(5))
				.andExpect(jsonPath("$.stats.hearts").value(5));
	}

	/** A replayed practice answer (same idempotency key) counts once in the lifetime totals. */
	@Test
	void replayedAnswerCountsOnceInLifetimeTotals() throws Exception {
		String auth = token();

		String round1 = mockMvc.perform(post("/v1/practice/sessions")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = JsonPath.read(round1, "$.sessionId");
		PracticeExercise exercise = practiceExercises
				.findBySessionIdAndRoundOrderByOrdinal(UUID.fromString(sessionId), 1).get(0);

		String answer = correctAnswerJson(exercise);
		submitPracticeAnswer(auth, sessionId, exercise, answer, "replay-key");
		submitPracticeAnswer(auth, sessionId, exercise, answer, "replay-key");

		String dashboard = getDashboard(auth);
		assertThat((int) JsonPath.read(dashboard, "$.totalAnswered")).isEqualTo(1);
		assertThat((int) JsonPath.read(dashboard, "$.totalCorrect")).isEqualTo(1);
	}

	@Test
	void practiceSessionMovesDashboardByExactlyTheDrivenAmounts() throws Exception {
		String auth = token();

		// Baseline: nothing driven yet.
		getDashboard(auth); // exercised for its own read-only test above; here just a sanity call

		String round1 = mockMvc.perform(post("/v1/practice/sessions")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = JsonPath.read(round1, "$.sessionId");
		List<PracticeExercise> generated = practiceExercises
				.findBySessionIdAndRoundOrderByOrdinal(UUID.fromString(sessionId), 1);

		// One right, one wrong — exactly two graded answers, one of them correct.
		PracticeExercise right = generated.get(0);
		PracticeExercise wrong = generated.get(1);
		submitPracticeAnswer(auth, sessionId, right, correctAnswerJson(right), "d-key-right");
		submitPracticeAnswer(auth, sessionId, wrong, wrongAnswerJson(wrong), "d-key-wrong");

		String afterAnswers = getDashboard(auth);
		assertThat((int) JsonPath.read(afterAnswers, "$.totalAnswered")).isEqualTo(2);
		assertThat((int) JsonPath.read(afterAnswers, "$.totalCorrect")).isEqualTo(1);
		assertThat((int) JsonPath.read(afterAnswers, "$.practiceSessionsCompleted")).isZero();
		// Both words are drawn from the session's pinned band (A1 for a fresh learner, no
		// earlier band to mix in) — exactly one distinct word now has times_correct > 0.
		Map<String, Integer> masteryByBand = masteryByBand(afterAnswers);
		assertThat(masteryByBand.get("A1")).isEqualTo(1);

		mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/complete")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"complete-d-1\"}"))
				.andExpect(status().isOk());

		String afterComplete = getDashboard(auth);
		assertThat((int) JsonPath.read(afterComplete, "$.practiceSessionsCompleted")).isEqualTo(1);
		// Completion itself grades no new answers.
		assertThat((int) JsonPath.read(afterComplete, "$.totalAnswered")).isEqualTo(2);
		assertThat((int) JsonPath.read(afterComplete, "$.totalCorrect")).isEqualTo(1);
	}

	/**
	 * Drives a real lesson session (mirrors {@code LessonSessionFlowIntegrationTest}'s generic
	 * MCQ discovery, so this stays valid against whatever content version is published) and
	 * checks the dashboard's lesson-completion and lifetime-total deltas.
	 */
	@Test
	void lessonCompletionMovesLessonsCompletedAndLifetimeTotals() throws Exception {
		String auth = token();
		McqFixture mcq = findMcq();

		String sessionBody = mockMvc.perform(post("/v1/sessions")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lessonId\":\"" + mcq.lessonId() + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = JsonPath.read(sessionBody, "$.id");

		mockMvc.perform(post("/v1/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + UUID.randomUUID() + "\",\"exerciseId\":\""
								+ mcq.exerciseId() + "\",\"answer\":{\"selectedOptionId\":\""
								+ mcq.correctOptionId() + "\"}}"))
				.andExpect(status().isOk());

		String before = getDashboard(auth);
		assertThat((int) JsonPath.read(before, "$.lessonsCompleted")).isZero();

		mockMvc.perform(post("/v1/sessions/" + sessionId + "/complete")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isOk());

		String after = getDashboard(auth);
		assertThat((int) JsonPath.read(after, "$.lessonsCompleted")).isEqualTo(1);
		assertThat((int) JsonPath.read(after, "$.totalAnswered")).isEqualTo(1);
		assertThat((int) JsonPath.read(after, "$.totalCorrect")).isEqualTo(1);
	}

	// ---- practice helpers (mirrors PracticeFlowIntegrationTest) ---------------------------

	private void submitPracticeAnswer(String auth, String sessionId, PracticeExercise exercise,
			String answerJson, String idempotencyKey) throws Exception {
		mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + idempotencyKey + "\","
								+ "\"exerciseId\":\"" + exercise.getId() + "\",\"answer\":" + answerJson
								+ "}"))
				.andExpect(status().isOk());
	}

	private String correctAnswerJson(PracticeExercise exercise) {
		Map<String, Object> key = exercise.getAnswerKey();
		return switch (exercise.getType()) {
			case WORD_MEANING, MEANING_WORD, SENTENCE_GAP ->
				"{\"selectedOptionId\":\"" + key.get("correctOptionId") + "\"}";
			case TYPE_WORD -> "{\"text\":\"" + firstAccepted(key) + "\"}";
			case SENTENCE_BUILD -> "{\"tokenOrder\":[" + java.util.Arrays
					.stream(firstAccepted(key).split("\\s+"))
					.map(t -> "\"" + t + "\"")
					.collect(java.util.stream.Collectors.joining(",")) + "]}";
		};
	}

	private String wrongAnswerJson(PracticeExercise exercise) {
		return switch (exercise.getType()) {
			case WORD_MEANING, MEANING_WORD, SENTENCE_GAP ->
				"{\"selectedOptionId\":\"" + UUID.randomUUID() + "\"}";
			case TYPE_WORD -> "{\"text\":\"zzz-not-a-word\"}";
			case SENTENCE_BUILD -> "{\"tokenOrder\":[\"zzz\"]}";
		};
	}

	private String firstAccepted(Map<String, Object> key) {
		return ((List<?>) key.get("accepted")).getFirst().toString();
	}

	private Map<String, Integer> masteryByBand(String dashboardJson) {
		List<Map<String, Object>> entries = JsonPath.read(dashboardJson, "$.wordMastery");
		return entries.stream().collect(java.util.stream.Collectors.toMap(
				e -> (String) e.get("cefrLevel"), e -> (Integer) e.get("mastered")));
	}

	// ---- lesson helpers (mirrors LessonSessionFlowIntegrationTest) ------------------------

	record McqFixture(UUID lessonId, UUID exerciseId, UUID correctOptionId) {
	}

	private McqFixture findMcq() {
		ContentVersion pub = versions.findFirstByStatus(ContentStatus.PUBLISHED).orElseThrow();
		return levels.findByContentVersionIdOrderByOrdinal(pub.getId()).stream()
				.flatMap(l -> units.findByLevelIdOrderByOrdinal(l.getId()).stream())
				.flatMap(u -> lessons.findByUnitIdOrderByOrdinal(u.getId()).stream())
				.flatMap(lesson -> exercises.findByLessonIdOrderByOrdinal(lesson.getId()).stream()
						.filter(e -> e.getType() == ExerciseType.MCQ)
						.map(e -> toFixture(lesson.getId(), e)))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("no MCQ in published content"));
	}

	private McqFixture toFixture(UUID lessonId, Exercise exercise) {
		UUID correct = options.findByExerciseIdOrderByOrdinal(exercise.getId()).stream()
				.filter(ExerciseOption::isCorrect)
				.findFirst().orElseThrow()
				.getId();
		return new McqFixture(lessonId, exercise.getId(), correct);
	}
}
