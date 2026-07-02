package com.shikhi.identity.service;

import com.shikhi.identity.domain.RefreshToken;
import com.shikhi.identity.repo.RefreshTokenRepository;
import com.shikhi.identity.security.JwtProperties;
import com.shikhi.platform.error.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates, and revokes refresh tokens. The raw token is a high-entropy random
 * string returned to the client; only its SHA-256 hash is persisted. Rotation revokes the
 * presented token and issues a successor in the same family; re-use of an already-revoked
 * token is treated as theft and revokes the entire family (ADR-0005).
 */
@Service
public class RefreshTokenService {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final RefreshTokenRepository repository;
	private final RefreshTokenFamilyRevoker familyRevoker;
	private final java.time.Duration refreshTtl;

	public RefreshTokenService(RefreshTokenRepository repository,
			RefreshTokenFamilyRevoker familyRevoker, JwtProperties props) {
		this.repository = repository;
		this.familyRevoker = familyRevoker;
		this.refreshTtl = props.getRefreshTtl();
	}

	/** Result of a successful rotation: who the token belongs to and the successor token. */
	public record RotationResult(UUID userId, String newRawToken) {
	}

	/** Mint a new refresh token in the given family and return the raw (unhashed) value. */
	@Transactional
	public String issue(UUID userId, UUID familyId, String deviceInfo) {
		String raw = generateRawToken();
		Instant expiresAt = Instant.now().plus(refreshTtl);
		repository.save(new RefreshToken(userId, sha256Hex(raw), familyId, expiresAt, deviceInfo));
		return raw;
	}

	@Transactional
	public RotationResult rotate(String rawToken) {
		RefreshToken current = repository.findByTokenHash(sha256Hex(rawToken))
				.orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
		Instant now = Instant.now();

		if (current.isRevoked()) {
			// A revoked token was presented again → likely theft. Burn the whole family in
			// its own committed transaction so the revocation survives the 401 that follows
			// (this request's transaction rolls back when the exception propagates).
			familyRevoker.revokeFamily(current.getFamilyId());
			throw ApiException.unauthorized("Refresh token replay detected");
		}
		if (current.isExpired(now)) {
			throw ApiException.unauthorized("Refresh token expired");
		}

		current.revoke();
		repository.save(current);
		String newRaw = issue(current.getUserId(), current.getFamilyId(), null);
		return new RotationResult(current.getUserId(), newRaw);
	}

	/** Revoke all active refresh tokens for a user (logout everywhere). */
	@Transactional
	public void revokeAllForUser(UUID userId) {
		repository.revokeAllForUser(userId, Instant.now());
	}

	private static String generateRawToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return URL_ENCODER.encodeToString(bytes);
	}

	private static String sha256Hex(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
