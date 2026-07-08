package com.shikhi.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end identity flows against a real Postgres (Flyway-migrated) + full security chain. */
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

	/** Registers a user and returns the raw JSON body of the token pair. */
	private String register(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"%s","displayName":"Rifat","uiLocale":"bn"}
								""".formatted(email, password)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.expiresIn").value(900))
				.andReturn();
		return result.getResponse().getContentAsString();
	}

	private static String field(String json, String path) {
		return JsonPath.read(json, path);
	}

	private static String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	@Test
	void registerThenAccessProfile() throws Exception {
		String tokens = register(uniqueEmail(), "s3cretpassword");

		String body = mockMvc.perform(get("/v1/me")
						.header(HttpHeaders.AUTHORIZATION, bearer(field(tokens, "$.accessToken"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.displayName").value("Rifat"))
				.andExpect(jsonPath("$.uiLocale").value("bn"))
				.andExpect(jsonPath("$.roles[0]").value("LEARNER"))
				.andExpect(jsonPath("$.joinedAt").isNotEmpty())
				.andReturn().getResponse().getContentAsString();

		// joinedAt is the account creation instant, E13 — must parse as an ISO instant.
		java.time.Instant.parse((String) field(body, "$.joinedAt"));
	}

	@Test
	void duplicateEmailIsConflict() throws Exception {
		String email = uniqueEmail();
		register(email, "s3cretpassword");

		mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"anotherpass"}
								""".formatted(email)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.correlationId").isNotEmpty());
	}

	@Test
	void weakPasswordIsRejected() throws Exception {
		mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"short"}
								""".formatted(uniqueEmail())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.details.password").isNotEmpty());
	}

	@Test
	void loginWithValidAndInvalidCredentials() throws Exception {
		String email = uniqueEmail();
		register(email, "s3cretpassword");

		mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"s3cretpassword"}
								""".formatted(email)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());

		mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"wrongpassword"}
								""".formatted(email)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void profileRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/v1/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	void updateProfileChangesNameAndLocale() throws Exception {
		String tokens = register(uniqueEmail(), "s3cretpassword");
		String auth = bearer(field(tokens, "$.accessToken"));

		mockMvc.perform(patch("/v1/me")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"displayName":"Nadia","uiLocale":"en"}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Nadia"))
				.andExpect(jsonPath("$.uiLocale").value("en"));
	}

	@Test
	void refreshRotatesAndDetectsReplay() throws Exception {
		String tokens = register(uniqueEmail(), "s3cretpassword");
		String firstRefresh = field(tokens, "$.refreshToken");

		// First rotation succeeds and returns a new refresh token.
		MvcResult rotated = mockMvc.perform(post("/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(firstRefresh)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn();
		String secondRefresh = field(rotated.getResponse().getContentAsString(), "$.refreshToken");

		// Re-using the now-rotated first token is a replay → 401.
		mockMvc.perform(post("/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(firstRefresh)))
				.andExpect(status().isUnauthorized());

		// Replay revoked the whole family, so the second token is now dead too.
		mockMvc.perform(post("/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(secondRefresh)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void exportReturnsOwnProfileAndUnmaskedIdentity() throws Exception {
		String email = uniqueEmail();
		String tokens = register(email, "s3cretpassword");
		String auth = bearer(field(tokens, "$.accessToken"));

		mockMvc.perform(get("/v1/me/export").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.profile.id").isNotEmpty())
				.andExpect(jsonPath("$.identities[0].provider").value("EMAIL"))
				.andExpect(jsonPath("$.identities[0].reference").value(email))
				.andExpect(jsonPath("$.exportedAt").isNotEmpty());
	}

	@Test
	void deleteAnonymizesIdentityAndRevokesSessions() throws Exception {
		String email = uniqueEmail();
		String tokens = register(email, "s3cretpassword");
		String auth = bearer(field(tokens, "$.accessToken"));
		String refresh = field(tokens, "$.refreshToken");

		mockMvc.perform(delete("/v1/me").header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isNoContent());

		// Email identity is gone → cannot log in anymore.
		mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email":"%s","password":"s3cretpassword"}
								""".formatted(email)))
				.andExpect(status().isUnauthorized());

		// Sessions revoked → the refresh token no longer works.
		mockMvc.perform(post("/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refresh)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void logoutRevokesRefreshTokens() throws Exception {
		String tokens = register(uniqueEmail(), "s3cretpassword");
		String auth = bearer(field(tokens, "$.accessToken"));
		String refresh = field(tokens, "$.refreshToken");

		mockMvc.perform(post("/v1/auth/logout")
						.header(HttpHeaders.AUTHORIZATION, auth))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"refreshToken":"%s"}
								""".formatted(refresh)))
				.andExpect(status().isUnauthorized());
	}
}
