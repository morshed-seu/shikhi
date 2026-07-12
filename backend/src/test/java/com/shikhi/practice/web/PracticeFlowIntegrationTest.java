package com.shikhi.practice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shikhi.content.repo.VocabularyRepository;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.domain.PracticeWordProgress;
import com.shikhi.practice.repo.PracticeExerciseRepository;
import com.shikhi.practice.repo.PracticeSessionRepository;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Adaptive practice end-to-end against real Postgres (journey J8): self-place → start →
 * answer right/wrong (hearts/XP/word mastery) → idempotent replay → next round (no word
 * repeats) → complete (idempotent) — plus the IDOR and no-answer-leak guarantees.
 */
class PracticeFlowIntegrationTest extends AbstractPracticeFlowIntegrationTest {

	@Autowired
	PracticeExerciseRepository exercises;

	@Autowired
	PracticeSessionRepository sessions;

	@Autowired
	PracticeWordProgressRepository wordProgress;

	@Autowired
	VocabularyRepository vocabulary;

	@Test
	void fullPracticeJourney() throws Exception {
		String auth = token();

		// Self-place at A2 (US-12.1).
		mockMvc.perform(put("/v1/stats/level")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"cefrLevel\":\"A2\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cefrLevel").value("A2"));

		// Start: round 1 arrives with the session, pinned to A2.
		String round1 = mockMvc.perform(post("/v1/practice/sessions")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.round").value(1))
				.andExpect(jsonPath("$.cefrLevel").value("A2"))
				.andExpect(jsonPath("$.exercises.length()").value(10))
				.andExpect(jsonPath("$.exercises[0].prompt.en").isNotEmpty())
				.andExpect(jsonPath("$.exercises[0].prompt.bn").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		// Correctness never reaches the client (answer key stays server-side).
		assertThat(round1).doesNotContain("correctOptionId", "accepted", "revealText",
				"answerKey");

		UUID sessionId = UUID.fromString(JsonPath.read(round1, "$.sessionId"));
		List<PracticeExercise> generated = exercises
				.findBySessionIdAndRoundOrderByOrdinal(sessionId, 1);
		assertThat(generated).hasSize(10);

		// ~70/30 band mix: both the current band and earlier-band review words appear.
		var bandsUsed = generated.stream()
				.map(e -> wordBand(e.getVocabularyId()))
				.collect(Collectors.toSet());
		assertThat(bandsUsed).contains("A2", "A1");

		// Right answer: +10 XP, full hearts, word mastery strengthened above baseline.
		PracticeExercise first = generated.get(0);
		String rightResult = submit(auth, sessionId, first, correctAnswerJson(first), "key-right");
		assertThat((boolean) JsonPath.read(rightResult, "$.verdict.correct")).isTrue();
		assertThat((int) JsonPath.read(rightResult, "$.stats.xp")).isEqualTo(10);
		assertThat((int) JsonPath.read(rightResult, "$.stats.hearts")).isEqualTo(5);
		assertThat(masteryScoreOf(first)).isEqualTo(3);

		// Wrong answer: heart lost, feedback reveals the correct answer, word weakened.
		PracticeExercise second = generated.get(1);
		String wrongResult = submit(auth, sessionId, second, wrongAnswerJson(second), "key-wrong");
		assertThat((boolean) JsonPath.read(wrongResult, "$.verdict.correct")).isFalse();
		assertThat((String) JsonPath.read(wrongResult, "$.verdict.feedback.en"))
				.startsWith("Correct answer:");
		assertThat((int) JsonPath.read(wrongResult, "$.stats.hearts")).isEqualTo(4);
		assertThat(masteryScoreOf(second)).isZero();

		// Idempotent replay: same key returns the original verdict, nothing charged twice.
		String replay = submit(auth, sessionId, second, wrongAnswerJson(second), "key-wrong");
		assertThat((boolean) JsonPath.read(replay, "$.verdict.correct")).isFalse();
		assertThat((int) JsonPath.read(replay, "$.stats.hearts")).isEqualTo(4);
		assertThat((int) JsonPath.read(replay, "$.stats.xp")).isEqualTo(10);

		// Keep going: round 2 brings 10 fresh words (no repeats within the session).
		String round2 = mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/rounds")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.round").value(2))
				.andExpect(jsonPath("$.exercises.length()").value(10))
				.andReturn().getResponse().getContentAsString();
		assertThat(round2).doesNotContain("correctOptionId");

		var round1Words = generated.stream().map(PracticeExercise::getVocabularyId)
				.collect(Collectors.toSet());
		var round2Words = exercises
				.findBySessionIdAndRoundOrderByOrdinal(sessionId, 2).stream()
				.map(PracticeExercise::getVocabularyId)
				.collect(Collectors.toSet());
		assertThat(round2Words).hasSize(10).doesNotContainAnyElementsOf(round1Words);

		// Complete: totals reflect the two graded answers; a second complete is idempotent.
		for (int i = 0; i < 2; i++) {
			mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/complete")
							.header(HttpHeaders.AUTHORIZATION, auth)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"idempotencyKey\":\"complete-1\"}"))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.correctCount").value(1))
					.andExpect(jsonPath("$.totalCount").value(2))
					.andExpect(jsonPath("$.roundsPlayed").value(2))
					.andExpect(jsonPath("$.xpEarned").value(10))
					.andExpect(jsonPath("$.levelUpEligible").value(false));
		}

		// A completed session accepts no further answers or rounds.
		mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/rounds")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isConflict());
	}

	@Test
	void anotherLearnerCannotDriveMySession() throws Exception {
		String owner = token();
		String body = mockMvc.perform(post("/v1/practice/sessions")
						.header(HttpHeaders.AUTHORIZATION, owner))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = JsonPath.read(body, "$.sessionId");
		String exerciseId = JsonPath.read(body, "$.exercises[0].id");

		String intruder = token();
		mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, intruder)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"x\",\"exerciseId\":\"" + exerciseId
								+ "\",\"answer\":{\"selectedOptionId\":\"" + UUID.randomUUID() + "\"}}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/rounds")
						.header(HttpHeaders.AUTHORIZATION, intruder))
				.andExpect(status().isNotFound());
	}

	@Test
	void levelValidationRejectsUnknownBands() throws Exception {
		mockMvc.perform(put("/v1/stats/level")
						.header(HttpHeaders.AUTHORIZATION, token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"cefrLevel\":\"C2\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void selfPlacesAtC1AndStartsARound() throws Exception {
		String auth = token();

		// C1 is a valid self-placement band (Oxford 5000, V17/V19).
		mockMvc.perform(put("/v1/stats/level")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"cefrLevel\":\"C1\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cefrLevel").value("C1"));

		// A round starts cleanly for the top band (BAND_ORDER includes C1).
		mockMvc.perform(post("/v1/practice/sessions")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.round").value(1))
				.andExpect(jsonPath("$.cefrLevel").value("C1"))
				.andExpect(jsonPath("$.exercises.length()").value(10));
	}

	private int masteryScoreOf(PracticeExercise exercise) {
		// Key on the owning learner too: the Postgres container is shared across the whole
		// test run, so another flow test may have progress rows for the same word.
		UUID userId = sessions.findById(exercise.getSessionId()).orElseThrow().getUserId();
		return wordProgress.findById(new PracticeWordProgress.Key(userId, exercise.getVocabularyId()))
				.orElseThrow()
				.getMasteryScore();
	}

	private String wordBand(UUID vocabularyId) {
		return vocabulary.findById(vocabularyId).orElseThrow().getCefrLevel();
	}
}
