package com.shikhi.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shikhi.identity.domain.Role;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit test — no Spring context, no database. */
class JwtServiceTest {

	private JwtService serviceWithSecret(String secret) {
		JwtProperties props = new JwtProperties();
		props.setSecret(secret);
		props.setIssuer("shikhi");
		props.setAccessTtl(Duration.ofMinutes(15));
		return new JwtService(props);
	}

	private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

	@Test
	void issuesAndParsesRoundTrip() {
		JwtService jwt = serviceWithSecret(SECRET);
		UUID userId = UUID.randomUUID();

		String token = jwt.issueAccessToken(userId, EnumSet.of(Role.LEARNER));
		AuthenticatedUser principal = jwt.parse(token);

		assertThat(principal.id()).isEqualTo(userId);
		assertThat(principal.roles()).containsExactly("LEARNER");
		assertThat(jwt.accessTtlSeconds()).isEqualTo(900);
	}

	@Test
	void rejectsTokenSignedWithDifferentSecret() {
		JwtService issuer = serviceWithSecret(SECRET);
		JwtService verifier = serviceWithSecret("a-totally-different-secret-key-9876543210zzz");
		String token = issuer.issueAccessToken(UUID.randomUUID(), EnumSet.of(Role.LEARNER));

		assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
	}

	@Test
	void rejectsGarbageToken() {
		JwtService jwt = serviceWithSecret(SECRET);
		assertThatThrownBy(() -> jwt.parse("not-a-jwt")).isInstanceOf(JwtException.class);
	}
}
