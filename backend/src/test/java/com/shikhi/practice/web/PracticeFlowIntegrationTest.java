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
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Adaptive practice end-to-end against real Postgres (journey J8): self-place → start →
 * answer right/wrong (hearts/XP/word strength) → idempotent replay → next round (no word
 * repeats) → complete (idempotent) — plus the IDOR and no-answer-leak guarantees.
 */
@AutoConfigureMockMvc
class PracticeFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	PracticeExerciseRepository exercises;

	@Autowired
	PracticeWordProgressRepository wordProgress;

	@Autowired
	VocabularyRepository vocabulary;

	/** Register a real learner and return their bearer token. */
	private String token() throws Exception {
		String email = "practicer-" + UUID.randomUUID() + "@example.com";
		String body = mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}

	/** Build a correct answer for any generated exercise from its server-side answer key. */
	private String correctAnswerJson(PracticeExercise exercise) {
		Map<String, Object> key = exercise.getAnswerKey();
		return switch (exercise.getType()) {
			case WORD_MEANING, MEANING_WORD, SENTENCE_GAP ->
				"{\"selectedOptionId\":\"" + key.get("correctOptionId") + "\"}";
			case TYPE_WORD -> "{\"text\":\"" + firstAccepted(key) + "\"}";
			case SENTENCE_BUILD -> "{\"tokenOrder\":[" + java.util.Arrays
					.stream(firstAccepted(key).split("\\s+"))
					.map(t -> "\"" + t + "\"")
					.collect(Collectors.joining(",")) + "]}";
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

	private String submit(String auth, String sessionId, String exerciseId, String answer,
			String idempotencyKey) throws Exception {
		return mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + idempotencyKey + "\","
								+ "\"exerciseId\":\"" + exerciseId + "\",\"answer\":" + answer + "}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

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

		String sessionId = JsonPath.read(round1, "$.sessionId");
		List<PracticeExercise> generated = exercises
				.findBySessionIdAndRoundOrderByOrdinal(UUID.fromString(sessionId), 1);
		assertThat(generated).hasSize(10);

		// ~70/30 band mix: both the current band and earlier-band review words appear.
		var bandsUsed = generated.stream()
				.map(e -> wordBand(e.getVocabularyId()))
				.collect(Collectors.toSet());
		assertThat(bandsUsed).contains("A2", "A1");

		// Right answer: +10 XP, full hearts, word strengthened above baseline.
		PracticeExercise first = generated.get(0);
		String rightResult = submit(auth, sessionId, first.getId().toString(),
				correctAnswerJson(first), "key-right");
		assertThat((boolean) JsonPath.read(rightResult, "$.verdict.correct")).isTrue();
		assertThat((int) JsonPath.read(rightResult, "$.stats.xp")).isEqualTo(10);
		assertThat((int) JsonPath.read(rightResult, "$.stats.hearts")).isEqualTo(5);
		assertThat(strengthOf(first)).isEqualTo(3);

		// Wrong answer: heart lost, feedback reveals the correct answer, word weakened.
		PracticeExercise second = generated.get(1);
		String wrongResult = submit(auth, sessionId, second.getId().toString(),
				wrongAnswerJson(second), "key-wrong");
		assertThat((boolean) JsonPath.read(wrongResult, "$.verdict.correct")).isFalse();
		assertThat((String) JsonPath.read(wrongResult, "$.verdict.feedback.en"))
				.startsWith("Correct answer:");
		assertThat((int) JsonPath.read(wrongResult, "$.stats.hearts")).isEqualTo(4);
		assertThat(strengthOf(second)).isZero();

		// Idempotent replay: same key returns the original verdict, nothing charged twice.
		String replay = submit(auth, sessionId, second.getId().toString(),
				wrongAnswerJson(second), "key-wrong");
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
				.findBySessionIdAndRoundOrderByOrdinal(UUID.fromString(sessionId), 2).stream()
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
						.content("{\"cefrLevel\":\"C1\"}"))
				.andExpect(status().isBadRequest());
	}

	private int strengthOf(PracticeExercise exercise) {
		return wordProgress.findAll().stream()
				.filter(p -> p.getKey().vocabularyId().equals(exercise.getVocabularyId()))
				.map(PracticeWordProgress::getStrength)
				.findFirst()
				.orElseThrow();
	}

	private String wordBand(UUID vocabularyId) {
		return vocabulary.findById(vocabularyId).orElseThrow().getCefrLevel();
	}
}
