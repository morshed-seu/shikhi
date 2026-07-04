package com.shikhi.content.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Vocabulary (dictionary) browser end-to-end against the seeded Oxford-3000 A1 band. */
@AutoConfigureMockMvc
class VocabularyFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtService jwtService;

	private String token() {
		return "Bearer " + jwtService.issueAccessToken(UUID.randomUUID(), EnumSet.of(Role.LEARNER));
	}

	@Test
	void requiresAuthentication() throws Exception {
		mockMvc.perform(get("/v1/vocabulary")).andExpect(status().isUnauthorized());
	}

	@Test
	void returnsSeededA1WordsInOrderWithBengaliAndExamples() throws Exception {
		mockMvc.perform(get("/v1/vocabulary?level=A1").header(HttpHeaders.AUTHORIZATION, token()))
				.andExpect(status().isOk())
				// The full A1 band is seeded (V12).
				.andExpect(jsonPath("$.length()").value(899))
				// Alphabetical order: 'a, an' sorts first.
				.andExpect(jsonPath("$[0].headword").value("a, an"))
				.andExpect(jsonPath("$[0].cefrLevel").value("A1"))
				.andExpect(jsonPath("$[0].bnGloss").isNotEmpty())
				.andExpect(jsonPath("$[0].exampleEn").isNotEmpty())
				.andExpect(jsonPath("$[0].exampleBn").isNotEmpty());
	}

	@Test
	void defaultsToA1WhenLevelOmitted() throws Exception {
		mockMvc.perform(get("/v1/vocabulary").header(HttpHeaders.AUTHORIZATION, token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].cefrLevel").value("A1"));
	}

	@Test
	void returnsSeededA2WordsInOrderWithBengaliAndExamples() throws Exception {
		mockMvc.perform(get("/v1/vocabulary?level=A2").header(HttpHeaders.AUTHORIZATION, token()))
				.andExpect(status().isOk())
				// The full A2 band is seeded (V13).
				.andExpect(jsonPath("$.length()").value(801))
				// Alphabetical order: 'ability' sorts first.
				.andExpect(jsonPath("$[0].headword").value("ability"))
				.andExpect(jsonPath("$[0].cefrLevel").value("A2"))
				.andExpect(jsonPath("$[0].bnGloss").isNotEmpty())
				.andExpect(jsonPath("$[0].exampleEn").isNotEmpty())
				.andExpect(jsonPath("$[0].exampleBn").isNotEmpty());
	}

	@Test
	void returnsSeededB1WordsInOrderWithBengaliAndExamples() throws Exception {
		mockMvc.perform(get("/v1/vocabulary?level=B1").header(HttpHeaders.AUTHORIZATION, token()))
				.andExpect(status().isOk())
				// The full B1 band is seeded (V14).
				.andExpect(jsonPath("$.length()").value(699))
				// Alphabetical order: 'absolutely' sorts first.
				.andExpect(jsonPath("$[0].headword").value("absolutely"))
				.andExpect(jsonPath("$[0].cefrLevel").value("B1"))
				.andExpect(jsonPath("$[0].bnGloss").isNotEmpty())
				.andExpect(jsonPath("$[0].exampleEn").isNotEmpty())
				.andExpect(jsonPath("$[0].exampleBn").isNotEmpty());
	}

	@Test
	void higherBandsAreEmptyUntilSeeded() throws Exception {
		mockMvc.perform(get("/v1/vocabulary?level=B2").header(HttpHeaders.AUTHORIZATION, token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void rejectsUnknownLevel() throws Exception {
		mockMvc.perform(get("/v1/vocabulary?level=Z9").header(HttpHeaders.AUTHORIZATION, token()))
				.andExpect(status().isBadRequest());
	}
}
