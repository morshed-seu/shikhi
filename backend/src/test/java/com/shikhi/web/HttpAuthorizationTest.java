package com.shikhi.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.identity.domain.Role;
import com.shikhi.identity.security.JwtService;
import com.shikhi.support.AbstractIntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Runs against the REAL servlet container (random port) using a plain JDK HTTP client, so the
 * actual servlet filter registration is exercised. This distinguishes an authenticated-but-
 * forbidden response (403) from an unauthenticated one (401); MockMvc bypasses servlet-level
 * filter registration and would miss a double-registered security filter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpAuthorizationTest {

	@LocalServerPort
	int port;

	@Autowired
	JwtService jwtService;

	private final HttpClient client = HttpClient.newHttpClient();

	@DynamicPropertySource
	static void datasource(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", AbstractIntegrationTest.POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", AbstractIntegrationTest.POSTGRES::getUsername);
		registry.add("spring.datasource.password", AbstractIntegrationTest.POSTGRES::getPassword);
	}

	private int status(String method, String path, Role role) throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path))
				.method(method, HttpRequest.BodyPublishers.noBody());
		if (role != null) {
			builder.header("Authorization",
					"Bearer " + jwtService.issueAccessToken(UUID.randomUUID(), EnumSet.of(role)));
		}
		return client.send(builder.build(), BodyHandlers.discarding()).statusCode();
	}

	@Test
	void authenticatedLearnerGetsForbiddenOnAdmin() throws Exception {
		assertThat(status("POST", "/v1/admin/content/drafts", Role.LEARNER)).isEqualTo(403);
	}

	@Test
	void anonymousGetsUnauthorizedOnAdmin() throws Exception {
		assertThat(status("POST", "/v1/admin/content/drafts", null)).isEqualTo(401);
	}

	@Test
	void authenticatedLearnerCanReadCurriculum() throws Exception {
		assertThat(status("GET", "/v1/curriculum", Role.LEARNER)).isEqualTo(200);
	}
}
