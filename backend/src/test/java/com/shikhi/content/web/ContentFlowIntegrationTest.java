package com.shikhi.content.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shikhi.identity.domain.Role;
import com.shikhi.identity.security.JwtService;
import com.shikhi.support.AbstractIntegrationTest;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Content module end-to-end. Read tests navigate the tree dynamically (they don't hardcode
 * seed ids or a specific version), so they stay correct regardless of which version another
 * test has published — keeping the suite order-independent against shared Postgres.
 */
@AutoConfigureMockMvc
class ContentFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtService jwtService;

	private String token(Role role) {
		return "Bearer " + jwtService.issueAccessToken(UUID.randomUUID(), EnumSet.of(role));
	}

	private String getCurriculum(String auth) throws Exception {
		return mockMvc.perform(get("/v1/curriculum").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();
	}

	@Test
	void curriculumRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/v1/curriculum"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void learnerSeesPublishedCurriculumTree() throws Exception {
		String body = getCurriculum(token(Role.LEARNER));
		String contentVersion = JsonPath.read(body, "$.contentVersion");
		String levelCode = JsonPath.read(body, "$.levels[0].code");
		String levelTitleEn = JsonPath.read(body, "$.levels[0].title.en");

		org.assertj.core.api.Assertions.assertThat(contentVersion).isNotBlank();
		org.assertj.core.api.Assertions.assertThat(levelCode).isNotBlank();
		org.assertj.core.api.Assertions.assertThat(levelTitleEn).isNotBlank();
	}

	@Test
	void lessonExposesExercisesButNotCorrectness() throws Exception {
		String auth = token(Role.LEARNER);
		String curriculum = getCurriculum(auth);
		String lessonId = JsonPath.read(curriculum, "$.levels[0].units[0].lessons[0].id");

		mockMvc.perform(get("/v1/lessons/" + lessonId).header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(lessonId))
				.andExpect(jsonPath("$.exercises[0].type").value("MCQ"))
				.andExpect(jsonPath("$.exercises[0].prompt.bn").isNotEmpty())
				// Option text is present, but the correctness flag must never be serialized.
				.andExpect(jsonPath("$.exercises[0].config.options[0].text.en").isNotEmpty())
				.andExpect(jsonPath("$.exercises[0].config.options[0].isCorrect").doesNotExist())
				.andExpect(jsonPath("$.exercises[0].config.options[0].is_correct").doesNotExist());
	}

	@Test
	void wordBankLessonExposesTokensButNotCorrectness() throws Exception {
		String auth = token(Role.LEARNER);
		String curriculum = getCurriculum(auth);
		// The Articles unit (A1-U2) opens with a lesson that contains a WORD_BANK exercise.
		java.util.List<String> lessonIds =
				JsonPath.read(curriculum, "$.levels[0].units[1].lessons[*].id");

		boolean sawWordBank = false;
		for (String lessonId : lessonIds) {
			String body = mockMvc.perform(
							get("/v1/lessons/" + lessonId).header(HttpHeaders.AUTHORIZATION, auth))
					.andExpect(status().isOk())
					.andReturn()
					.getResponse()
					.getContentAsString();
			java.util.List<String> tokens =
					JsonPath.read(body, "$.exercises[?(@.type=='WORD_BANK')].config.tokens[*].text.en");
			if (!tokens.isEmpty()) {
				sawWordBank = true;
				// Tokens are render data; correctness flags and accepted answers never ship.
				org.assertj.core.api.Assertions.assertThat(
								JsonPath.<java.util.List<Object>>read(body,
										"$.exercises[?(@.type=='WORD_BANK')].config.tokens[*].isCorrect"))
						.isEmpty();
				org.assertj.core.api.Assertions.assertThat(body).doesNotContain("acceptedAnswer");
			}
		}
		org.assertj.core.api.Assertions.assertThat(sawWordBank).isTrue();
	}

	@Test
	void unknownLessonIsNotFound() throws Exception {
		mockMvc.perform(get("/v1/lessons/" + UUID.randomUUID())
						.header(HttpHeaders.AUTHORIZATION, token(Role.LEARNER)))
				.andExpect(status().isNotFound());
	}

	@Test
	void learnerCannotAuthor() throws Exception {
		mockMvc.perform(post("/v1/admin/content/drafts")
						.header(HttpHeaders.AUTHORIZATION, token(Role.LEARNER)))
				.andExpect(status().isForbidden());
	}

	@Test
	void authorCanBranchValidatePublishAndCacheInvalidates() throws Exception {
		String author = token(Role.AUTHOR);

		// Branch a draft from the current published version.
		MvcResult created = mockMvc.perform(post("/v1/admin/content/drafts")
						.header(HttpHeaders.AUTHORIZATION, author))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andReturn();
		String draftId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
		String draftLabel = JsonPath.read(created.getResponse().getContentAsString(), "$.label");

		// The clone of valid content validates cleanly.
		mockMvc.perform(post("/v1/admin/content/drafts/" + draftId + "/validate")
						.header(HttpHeaders.AUTHORIZATION, author))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.valid").value(true));

		// Publish it.
		mockMvc.perform(post("/v1/admin/content/drafts/" + draftId + "/publish")
						.header(HttpHeaders.AUTHORIZATION, author))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PUBLISHED"));

		// Cache was invalidated: the curriculum now reflects the newly published version.
		String afterPublish = getCurriculum(token(Role.LEARNER));
		String nowVersion = JsonPath.read(afterPublish, "$.contentVersion");
		org.assertj.core.api.Assertions.assertThat(nowVersion).isEqualTo(draftLabel);
	}

	@Test
	void republishingAnAlreadyPublishedVersionConflicts() throws Exception {
		String author = token(Role.AUTHOR);
		MvcResult created = mockMvc.perform(post("/v1/admin/content/drafts")
						.header(HttpHeaders.AUTHORIZATION, author))
				.andExpect(status().isCreated())
				.andReturn();
		String draftId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

		mockMvc.perform(post("/v1/admin/content/drafts/" + draftId + "/publish")
						.header(HttpHeaders.AUTHORIZATION, author))
				.andExpect(status().isOk());
		// Second publish of the same (now PUBLISHED) version is rejected.
		mockMvc.perform(post("/v1/admin/content/drafts/" + draftId + "/publish")
						.header(HttpHeaders.AUTHORIZATION, author))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_DRAFT"));
	}
}
