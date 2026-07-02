package com.shikhi.identity.security;

import com.shikhi.identity.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

/** Issues and verifies short-lived access JWTs (HS256). Stateless — the hot auth path. */
@Service
public class JwtService {

	private final SecretKey key;
	private final String issuer;
	private final long accessTtlSeconds;

	public JwtService(JwtProperties props) {
		this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
		this.issuer = props.getIssuer();
		this.accessTtlSeconds = props.getAccessTtl().toSeconds();
	}

	public long accessTtlSeconds() {
		return accessTtlSeconds;
	}

	public String issueAccessToken(UUID userId, Set<Role> roles) {
		Instant now = Instant.now();
		List<String> roleNames = roles.stream().map(Enum::name).toList();
		return Jwts.builder()
				.issuer(issuer)
				.subject(userId.toString())
				.claim("roles", roleNames)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
				.signWith(key)
				.compact();
	}

	/**
	 * Verify a signed access token and extract the principal.
	 *
	 * @throws JwtException if the token is malformed, tampered, or expired
	 */
	@SuppressWarnings("unchecked")
	public AuthenticatedUser parse(String token) {
		Jws<Claims> jws = Jwts.parser()
				.verifyWith(key)
				.requireIssuer(issuer)
				.build()
				.parseSignedClaims(token);
		Claims claims = jws.getPayload();
		UUID userId = UUID.fromString(claims.getSubject());
		List<String> roles = claims.get("roles", List.class);
		return new AuthenticatedUser(userId, roles == null ? List.of() : List.copyOf(roles));
	}
}
