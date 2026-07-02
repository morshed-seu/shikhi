package com.shikhi.review.web;

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
import com.shikhi.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Review queue end-to-end: missing an exercise in a lesson schedules it, it surfaces in the
 * due queue, and recalling it correctly reschedules it out of view. Discovers a gradable MCQ
 * from the published tree so it stays order-independent on the shared container.
 */
@AutoConfigureMockMvc
class ReviewFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

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

	record McqFixture(UUID lessonId, UUID exerciseId, UUID wrongOptionId) {
	}

	private String token() throws Exception {
		String email = "review-" + UUID.randomUUID() + "@example.com";
		String body = mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.read(body, "$.accessToken");
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
				.orElseThrow();
	}

	private McqFixture toFixture(UUID lessonId, Exercise exercise) {
		UUID wrong = options.findByExerciseIdOrderByOrdinal(exercise.getId()).stream()
				.filter(o -> !o.isCorrect()).findFirst().orElseThrow().getId();
		return new McqFixture(lessonId, exercise.getId(), wrong);
	}

	private void submitWrong(String auth, String sessionId, McqFixture mcq) throws Exception {
		mockMvc.perform(post("/v1/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + UUID.randomUUID() + "\",\"exerciseId\":\""
								+ mcq.exerciseId() + "\",\"answer\":{\"selectedOptionId\":\""
								+ mcq.wrongOptionId() + "\"}}"))
				.andExpect(status().isOk());
	}

	private String startSession(String auth, UUID lessonId) throws Exception {
		String body = mockMvc.perform(post("/v1/sessions")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lessonId\":\"" + lessonId + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	@Test
	void dueQueueRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/v1/review/due")).andExpect(status().isUnauthorized());
	}

	@Test
	void missedExerciseSurfacesInReviewThenRecallReschedulesIt() throws Exception {
		String auth = token();
		McqFixture mcq = findMcq();

		// Nothing due yet.
		mockMvc.perform(get("/v1/review/due").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		// Miss the exercise in a lesson, then complete it.
		String sessionId = startSession(auth, mcq.lessonId());
		submitWrong(auth, sessionId, mcq);
		mockMvc.perform(post("/v1/sessions/" + sessionId + "/complete")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reviewItemsAdded").value(1));

		// It now surfaces in the due queue, in box 1, with a prompt.
		mockMvc.perform(get("/v1/review/due").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].exerciseId").value(mcq.exerciseId().toString()))
				.andExpect(jsonPath("$[0].boxLevel").value(1))
				.andExpect(jsonPath("$[0].prompt.bn").isNotEmpty());

		// Recall it correctly → it is promoted and scheduled out of the due window.
		mockMvc.perform(post("/v1/review/results")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"results\":[{\"exerciseId\":\"" + mcq.exerciseId()
								+ "\",\"correct\":true}]}"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/v1/review/due").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}
}
