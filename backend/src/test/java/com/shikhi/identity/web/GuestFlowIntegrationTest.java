package com.shikhi.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shikhi.identity.domain.Locale;
import com.shikhi.identity.domain.Role;
import com.shikhi.identity.domain.User;
import com.shikhi.identity.domain.UserStatus;
import com.shikhi.identity.repo.UserRepository;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.repo.PracticeExerciseRepository;
import com.shikhi.support.AbstractIntegrationTest;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Guest (anonymous) learning + claim-on-signup (ADR-0011). Proves the headline promise: a guest
 * learns against the real engine, then claiming upgrades the SAME user id in place — no progress
 * is migrated. Also covers the conflict cases and the abandoned-guest reaper query.
 */
@AutoConfigureMockMvc
class GuestFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	PracticeExerciseRepository exercises;

	@Autowired
	UserRepository users;

	private static String uniqueEmail() {
		return "guest-" + UUID.randomUUID() + "@example.com";
	}

	private static String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	/** Start a guest session and return the token-pair JSON body. */
	private String startGuest() throws Exception {
		return mockMvc.perform(post("/v1/auth/guest")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"uiLocale\":\"bn\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
	}

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

	private String firstAccepted(Map<String, Object> key) {
		return ((List<?>) key.get("accepted")).getFirst().toString();
	}

	@Test
	void guestLearnsThenClaimPreservesSameUserAndProgress() throws Exception {
		String guestTokens = startGuest();
		String guestAuth = bearer(JsonPath.read(guestTokens, "$.accessToken"));

		// /me shows an anonymous learner with the full learning role.
		String guestId = mockMvc.perform(get("/v1/me")
						.header(HttpHeaders.AUTHORIZATION, guestAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isGuest").value(true))
				.andExpect(jsonPath("$.roles[0]").value("LEARNER"))
				.andReturn().getResponse().getContentAsString();
		String userId = JsonPath.read(guestId, "$.id");

		// Guest drives the real practice engine: start a session and answer one item correctly.
		String round1 = mockMvc.perform(post("/v1/practice/sessions")
						.header(HttpHeaders.AUTHORIZATION, guestAuth))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String sessionId = JsonPath.read(round1, "$.sessionId");
		PracticeExercise first = exercises
				.findBySessionIdAndRoundOrderByOrdinal(UUID.fromString(sessionId), 1).get(0);
		mockMvc.perform(post("/v1/practice/sessions/" + sessionId + "/answers")
						.header(HttpHeaders.AUTHORIZATION, guestAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"idempotencyKey\":\"g1\",\"exerciseId\":\"" + first.getId()
								+ "\",\"answer\":" + correctAnswerJson(first) + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict.correct").value(true))
				.andExpect(jsonPath("$.stats.xp").value(10));

		// Claim: add email+password. Same user id, now ACTIVE and no longer a guest.
		String email = uniqueEmail();
		String claimed = mockMvc.perform(post("/v1/auth/claim")
						.header(HttpHeaders.AUTHORIZATION, guestAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"s3cretpassword\","
								+ "\"displayName\":\"Rifat\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		String claimedAuth = bearer(JsonPath.read(claimed, "$.accessToken"));

		mockMvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, claimedAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId)) // SAME id — upgraded in place
				.andExpect(jsonPath("$.isGuest").value(false))
				.andExpect(jsonPath("$.displayName").value("Rifat"));

		// The XP earned as a guest is still there — nothing was migrated, it was always this user.
		mockMvc.perform(get("/v1/stats").header(HttpHeaders.AUTHORIZATION, claimedAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.xp").value(10));

		// And the new credentials log into that very same account.
		String loggedIn = mockMvc.perform(post("/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		mockMvc.perform(get("/v1/me")
						.header(HttpHeaders.AUTHORIZATION, bearer(JsonPath.read(loggedIn, "$.accessToken"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(userId));
	}

	@Test
	void claimWithAlreadyRegisteredEmailIsConflict() throws Exception {
		// An existing account owns this email.
		String email = uniqueEmail();
		mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isCreated());

		String guestAuth = bearer(JsonPath.read(startGuest(), "$.accessToken"));
		mockMvc.perform(post("/v1/auth/claim")
						.header(HttpHeaders.AUTHORIZATION, guestAuth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + email + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

		// The guest session is untouched — still a usable guest.
		mockMvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, guestAuth))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isGuest").value(true));
	}

	@Test
	void claimOnAlreadyFullAccountIsConflict() throws Exception {
		String registered = mockMvc.perform(post("/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String auth = bearer(JsonPath.read(registered, "$.accessToken"));

		mockMvc.perform(post("/v1/auth/claim")
						.header(HttpHeaders.AUTHORIZATION, auth)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_CLAIMED"));
	}

	@Test
	void claimRequiresAuthentication() throws Exception {
		mockMvc.perform(post("/v1/auth/claim")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"s3cretpassword\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@Transactional
	void reaperRemovesStaleGuestsButKeepsRecentOnes() throws Exception {
		User guest = User.anonymous(Locale.BN);
		guest.addRole(Role.LEARNER);
		UUID id = users.save(guest).getId();

		// A recent guest (updated just now) survives a cutoff in the past.
		users.deleteStaleByStatus(UserStatus.ANONYMOUS, Instant.now().minusSeconds(3600));
		assertThat(users.existsById(id)).isTrue();

		// Once it counts as stale (cutoff in the future), the reaper removes it.
		users.deleteStaleByStatus(UserStatus.ANONYMOUS, Instant.now().plusSeconds(3600));
		assertThat(users.existsById(id)).isFalse();
	}
}
