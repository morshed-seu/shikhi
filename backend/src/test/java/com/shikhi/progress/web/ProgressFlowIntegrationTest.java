package com.shikhi.progress.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shikhi.support.AbstractIntegrationTest;
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
}
