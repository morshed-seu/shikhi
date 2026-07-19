package com.shikhi.progress.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shikhi.content.repo.VocabularyRepository;
import com.shikhi.practice.domain.PracticeWordProgress;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.practice.schedule.ReviewProgress;
import com.shikhi.practice.schedule.ReviewProgressRepository;
import com.shikhi.support.AbstractIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Progress & gamification end-to-end against real Postgres. Each test registers a fresh
 * learner (so users are isolated) and navigates the published tree via the API, staying
 * order-independent on the shared container.
 */
@AutoConfigureMockMvc
class ProgressFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	VocabularyRepository vocabulary;

	@Autowired
	PracticeWordProgressRepository wordProgress;

	@Autowired
	ReviewProgressRepository reviewProgress;

	private static final String U0 = "$.levels[0].units[0]";

	private String token() throws Exception {
		String email = "prog-" + UUID.randomUUID() + "@example.com";
		String body = mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
	}

	private String curriculum(String auth) throws Exception {
		return mockMvc.perform(get("/v1/curriculum").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private String startSession(String auth, String lessonId) throws Exception {
		String body = mockMvc.perform(post("/v1/sessions")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lessonId\":\"" + lessonId + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	/** Reads the JWT {@code sub} claim (userId) straight out of the access token, unsigned —
	 * fine for a test assertion, no verification needed since we just minted it ourselves. */
	private UUID userIdFrom(String auth) {
		String jwt = auth.substring("Bearer ".length());
		String payloadJson = new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
		return UUID.fromString(JsonPath.read(payloadJson, "$.sub"));
	}

	@Test
	void statsRequireAuthAndStartAtZero() throws Exception {
		mockMvc.perform(get("/v1/stats")).andExpect(status().isUnauthorized());

		mockMvc.perform(get("/v1/stats").header(HttpHeaders.AUTHORIZATION, token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.xp").value(0))
				.andExpect(jsonPath("$.currentStreak").value(0))
				.andExpect(jsonPath("$.hearts").value(5));
	}

	@Test
	void completingALessonAdvancesStreakAndUnlocksTheNextLesson() throws Exception {
		String auth = token();
		String before = curriculum(auth);
		String lesson0 = JsonPath.read(before, U0 + ".lessons[0].id");

		// Fresh learner: first lesson open, second locked.
		org.assertj.core.api.Assertions.assertThat((Boolean) JsonPath.read(before, U0 + ".lessons[0].locked")).isFalse();
		org.assertj.core.api.Assertions.assertThat((Boolean) JsonPath.read(before, U0 + ".lessons[1].locked")).isTrue();

		String sessionId = startSession(auth, lesson0);
		mockMvc.perform(post("/v1/sessions/" + sessionId + "/complete")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.newlyUnlocked", org.hamcrest.Matchers.hasSize(1)));

		mockMvc.perform(get("/v1/stats").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(jsonPath("$.currentStreak").value(1));

		String after = curriculum(auth);
		org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(after, U0 + ".lessons[0].status")).isEqualTo("COMPLETED");
		org.assertj.core.api.Assertions.assertThat((Boolean) JsonPath.read(after, U0 + ".lessons[1].locked")).isFalse();
	}

	@Test
	void completingAnAlreadyCompletedSessionAwardsNoExtraXp() throws Exception {
		String auth = token();
		String lesson0 = JsonPath.read(curriculum(auth), U0 + ".lessons[0].id");
		String sessionId = startSession(auth, lesson0);
		String url = "/v1/sessions/" + sessionId + "/complete";

		mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"c1\"}"))
				.andExpect(status().isOk());
		// Replaying completion awards nothing new.
		mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content("{\"idempotencyKey\":\"c2\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.xpEarned").value(0));
	}

	@Test
	void syncAppliesBufferedCompletionsExactlyOnce() throws Exception {
		String auth = token();
		String lesson0 = JsonPath.read(curriculum(auth), U0 + ".lessons[0].id");
		String batch = "{\"events\":[{\"idempotencyKey\":\"s1\",\"type\":\"COMPLETE_LESSON\","
				+ "\"payload\":{\"lessonId\":\"" + lesson0 + "\",\"score\":2}}]}";

		mockMvc.perform(post("/v1/progress/sync").header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content(batch))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.xp").value(20))
				.andExpect(jsonPath("$.currentStreak").value(1));

		// Replaying the same batch is a no-op — XP does not double.
		mockMvc.perform(post("/v1/progress/sync").header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content(batch))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.xp").value(20));
	}

	/**
	 * OF5 (doc 93 §5): a {@code PRACTICE_ANSWER} sync event — the shape OF4's offline Kotlin
	 * client buffers — must award XP via {@link com.shikhi.progress.service.ProgressService}
	 * AND advance mastery/the review ladder via
	 * {@link com.shikhi.practice.service.WordProgressService}, both through the real
	 * {@code POST /v1/progress/sync} endpoint against real Postgres, and honor the optional
	 * {@code answeredAt} instead of stamping sync time.
	 */
	@Test
	void syncAppliesPracticeAnswerEventsUpdatingXpAndWordMasteryAndReviewLadder() throws Exception {
		String auth = token();
		UUID userId = userIdFrom(auth);
		UUID vocabularyId = vocabulary.findAll().get(0).getId();
		Instant answeredAt = Instant.parse("2026-07-16T10:15:00Z"); // two days before "today"

		String batch = "{\"events\":[" + practiceAnswerEvent("pa1", vocabularyId, true, answeredAt)
				+ "," + practiceAnswerEvent("pa2", vocabularyId, true, answeredAt) + ","
				+ practiceAnswerEvent("pa3", vocabularyId, true, answeredAt) + "]}";

		// Three correct answers: +10 XP each (recordPracticeAnswer), and — since default
		// graduation thresholds are mastery>=3/timesCorrect>=2/timesSeen>=3, an unseen word
		// starting at mastery 2 hits all three on the third correct answer — a review-ladder
		// row appears too (WordProgressService.recordAnswer), proving both paths ran.
		mockMvc.perform(post("/v1/progress/sync").header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content(batch))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.xp").value(30))
				.andExpect(jsonPath("$.hearts").value(5));

		PracticeWordProgress mastery = wordProgress
				.findById(new PracticeWordProgress.Key(userId, vocabularyId)).orElseThrow();
		assertThat(mastery.getMasteryScore()).isEqualTo(5); // 2 +1 +1 +1
		assertThat(mastery.getTimesCorrect()).isEqualTo(3);
		// answeredAt propagated, not sync-time "now".
		assertThat(mastery.getLastSeenAt()).isEqualTo(answeredAt);

		ReviewProgress graduated = reviewProgress
				.findById(new ReviewProgress.Key(userId, vocabularyId)).orElseThrow();
		assertThat(graduated.getReviewStage()).isEqualTo(1);
		assertThat(graduated.getDueAt()).isEqualTo(answeredAt.plus(Duration.ofDays(1)));

		// Replaying the same batch is a no-op — XP and mastery do not double.
		mockMvc.perform(post("/v1/progress/sync").header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content(batch))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.xp").value(30));
		assertThat(wordProgress.findById(new PracticeWordProgress.Key(userId, vocabularyId))
				.orElseThrow().getTimesCorrect()).isEqualTo(3);
	}

	@Test
	void syncAppliesAWrongPracticeAnswerLosingAHeartAndWeakeningMastery() throws Exception {
		String auth = token();
		UUID userId = userIdFrom(auth);
		UUID vocabularyId = vocabulary.findAll().get(1).getId();
		Instant answeredAt = Instant.parse("2026-07-17T09:00:00Z");

		String batch = "{\"events\":["
				+ practiceAnswerEvent("wa1", vocabularyId, false, answeredAt) + "]}";

		mockMvc.perform(post("/v1/progress/sync").header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON).content(batch))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.xp").value(0))
				.andExpect(jsonPath("$.hearts").value(4));

		PracticeWordProgress mastery = wordProgress
				.findById(new PracticeWordProgress.Key(userId, vocabularyId)).orElseThrow();
		assertThat(mastery.getMasteryScore()).isZero(); // 2 - 2
		assertThat(mastery.getTimesWrong()).isEqualTo(1);
	}

	private String practiceAnswerEvent(String idempotencyKey, UUID vocabularyId, boolean correct,
			Instant answeredAt) {
		return "{\"idempotencyKey\":\"" + idempotencyKey + "\",\"type\":\"PRACTICE_ANSWER\","
				+ "\"payload\":{\"vocabularyId\":\"" + vocabularyId + "\",\"correct\":" + correct
				+ ",\"answeredAt\":\"" + answeredAt + "\"}}";
	}
}
