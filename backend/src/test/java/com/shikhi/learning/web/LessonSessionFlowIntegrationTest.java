package com.shikhi.learning.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shikhi.content.domain.ContentStatus;
import com.shikhi.content.domain.ContentVersion;
import com.shikhi.content.domain.Exercise;
import com.shikhi.content.domain.ExerciseType;
import com.shikhi.content.domain.ExerciseOption;
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
 * Lesson session end-to-end against real Postgres. Discovers a gradable MCQ (with its correct
 * and wrong option ids) directly from the repositories, so the test works against whatever
 * version is currently published — staying order-independent on the shared container.
 */
@AutoConfigureMockMvc
class LessonSessionFlowIntegrationTest extends AbstractIntegrationTest {

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

	/** A gradable MCQ discovered from the published tree. */
	record McqFixture(UUID lessonId, UUID exerciseId, UUID correctOptionId, UUID wrongOptionId) {
	}

	/** Register a real learner (the session's user_id must exist) and return their bearer token. */
	private String token() throws Exception {
		String email = "learner-" + UUID.randomUUID() + "@example.com";
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
				.orElseThrow(() -> new IllegalStateException("no MCQ in published content"));
	}

	private McqFixture toFixture(UUID lessonId, Exercise exercise) {
		var opts = options.findByExerciseIdOrderByOrdinal(exercise.getId());
		UUID correct = opts.stream().filter(ExerciseOption::isCorrect).findFirst().orElseThrow()
				.getId();
		UUID wrong = opts.stream().filter(o -> !o.isCorrect()).findFirst().orElseThrow().getId();
		return new McqFixture(lessonId, exercise.getId(), correct, wrong);
	}

	private String startSession(String auth, UUID lessonId) throws Exception {
		String body = mockMvc.perform(post("/v1/sessions")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lessonId\":\"" + lessonId + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.heartsRemaining").value(5))
				.andExpect(jsonPath("$.contentVersion").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private String answerBody(String key, UUID exerciseId, UUID optionId) {
		return "{\"idempotencyKey\":\"" + key + "\",\"exerciseId\":\"" + exerciseId
				+ "\",\"answer\":{\"selectedOptionId\":\"" + optionId + "\"}}";
	}

	@Test
	void startRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/v1/sessions").contentType(MediaType.APPLICATION_JSON)
						.content("{\"lessonId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void startingAnUnknownLessonIsNotFound() throws Exception {
		mockMvc.perform(post("/v1/sessions").header(HttpHeaders.AUTHORIZATION, token())
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"lessonId\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void correctAnswerScoresAndKeepsHeartsThenCompletes() throws Exception {
		McqFixture mcq = findMcq();
		String auth = token();
		String sessionId = startSession(auth, mcq.lessonId());

		mockMvc.perform(post("/v1/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(answerBody(UUID.randomUUID().toString(), mcq.exerciseId(),
								mcq.correctOptionId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict.correct").value(true))
				.andExpect(jsonPath("$.verdict.source").value("RULE"))
				.andExpect(jsonPath("$.verdict.feedback.bn").isNotEmpty())
				.andExpect(jsonPath("$.stats.hearts").value(5));

		mockMvc.perform(post("/v1/sessions/" + sessionId + "/complete")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.score").value(1))
				.andExpect(jsonPath("$.xpEarned").value(10));
	}

	@Test
	void wrongAnswerCostsAHeartAndReturnsFeedback() throws Exception {
		McqFixture mcq = findMcq();
		String auth = token();
		String sessionId = startSession(auth, mcq.lessonId());

		mockMvc.perform(post("/v1/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(answerBody(UUID.randomUUID().toString(), mcq.exerciseId(),
								mcq.wrongOptionId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict.correct").value(false))
				.andExpect(jsonPath("$.verdict.feedback.en").isNotEmpty())
				.andExpect(jsonPath("$.stats.hearts").value(4));
	}

	@Test
	void resubmittingWithTheSameKeyIsIdempotent() throws Exception {
		McqFixture mcq = findMcq();
		String auth = token();
		String sessionId = startSession(auth, mcq.lessonId());
		String key = UUID.randomUUID().toString();

		mockMvc.perform(post("/v1/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(answerBody(key, mcq.exerciseId(), mcq.wrongOptionId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stats.hearts").value(4));

		// Same idempotency key → same verdict, hearts NOT charged again.
		mockMvc.perform(post("/v1/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content(answerBody(key, mcq.exerciseId(), mcq.wrongOptionId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict.correct").value(false))
				.andExpect(jsonPath("$.stats.hearts").value(4));
	}

	@Test
	void anotherLearnerCannotDriveTheSession() throws Exception {
		McqFixture mcq = findMcq();
		String owner = token();
		String sessionId = startSession(owner, mcq.lessonId());

		mockMvc.perform(post("/v1/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, token()) // a different learner
						.contentType(MediaType.APPLICATION_JSON)
						.content(answerBody(UUID.randomUUID().toString(), mcq.exerciseId(),
								mcq.correctOptionId())))
				.andExpect(status().isNotFound());
	}
}
