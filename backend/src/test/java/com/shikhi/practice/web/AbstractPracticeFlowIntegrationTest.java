package com.shikhi.practice.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared MockMvc helpers for the practice-flow E2E suites (doc 43 §4 Fix 7g): registering a
 * learner, building correct/wrong answer bodies from a generated exercise's server-side answer
 * key, and submitting an answer — previously duplicated verbatim between {@link
 * PracticeFlowIntegrationTest} (legacy/flag-off path) and {@link
 * PlannerPracticeFlowIntegrationTest} (plan-backed path). Each subclass keeps its own {@code
 * @TestPropertySource}/{@code @TestConfiguration} since the two suites deliberately run in
 * different Spring contexts.
 */
@AutoConfigureMockMvc
abstract class AbstractPracticeFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	/** Register a real learner and return their bearer token. */
	String token() throws Exception {
		String email = "practicer-" + UUID.randomUUID() + "@example.com";
		String body = mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}

	/** Build a correct answer for any generated exercise from its server-side answer key. */
	String correctAnswerJson(PracticeExercise exercise) {
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

	String wrongAnswerJson(PracticeExercise exercise) {
		return switch (exercise.getType()) {
			case WORD_MEANING, MEANING_WORD, SENTENCE_GAP ->
				"{\"selectedOptionId\":\"" + UUID.randomUUID() + "\"}";
			case TYPE_WORD -> "{\"text\":\"zzz-not-a-word\"}";
			case SENTENCE_BUILD -> "{\"tokenOrder\":[\"zzz\"]}";
		};
	}

	String firstAccepted(Map<String, Object> key) {
		return ((List<?>) key.get("accepted")).getFirst().toString();
	}

	/** Submits an answer and returns the raw response body (verdict/stats). */
	String submit(String auth, UUID sessionId, PracticeExercise exercise, String answerJson,
			String idempotencyKey) throws Exception {
		return mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + idempotencyKey + "\","
								+ "\"exerciseId\":\"" + exercise.getId() + "\",\"answer\":" + answerJson + "}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}
}
