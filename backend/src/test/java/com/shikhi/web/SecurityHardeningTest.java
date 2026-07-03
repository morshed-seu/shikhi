package com.shikhi.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

/**
 * Phase-E hardening: defence-in-depth response headers on every response, and actuator
 * lockdown (probes public; metrics/info ADMIN-only so they aren't publicly scrapable).
 */
@AutoConfigureMockMvc
class SecurityHardeningTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JwtService jwtService;

	private String token(Role role) {
		return "Bearer " + jwtService.issueAccessToken(UUID.randomUUID(), EnumSet.of(role));
	}

	@Test
	void securityHeadersArePresent() throws Exception {
		// HSTS is only emitted over HTTPS, so exercise a secure request.
		mockMvc.perform(get("/v1/health").secure(true))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("Content-Security-Policy",
						"default-src 'none'; frame-ancestors 'none'"))
				.andExpect(header().string("Referrer-Policy", "no-referrer"))
				.andExpect(header().exists("Strict-Transport-Security"));
	}

	@Test
	void livenessAndReadinessProbesArePublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
		mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
	}

	@Test
	void metricsEndpointIsNotPubliclyScrapable() throws Exception {
		// Authorization rejects anonymous/under-privileged callers before the endpoint dispatches.
		mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
		// A learner token is authenticated but under-privileged → 403.
		mockMvc.perform(get("/actuator/prometheus").header(HttpHeaders.AUTHORIZATION, token(Role.LEARNER)))
				.andExpect(status().isForbidden());
		// An admin passes the lockdown (info is always mapped; prometheus scrape verified live).
		mockMvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, token(Role.ADMIN)))
				.andExpect(status().isOk());
	}
}
