package com.shikhi.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A single issued refresh token, stored as a SHA-256 hex hash. Tokens in one rotation
 * chain share a {@code familyId}; presenting an already-rotated (revoked) token is a
 * replay signal that revokes the whole family.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "token_hash", nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt = Instant.now();

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "device_info")
	private String deviceInfo;

	protected RefreshToken() {
		// for JPA
	}

	public RefreshToken(UUID userId, String tokenHash, UUID familyId, Instant expiresAt,
			String deviceInfo) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.expiresAt = expiresAt;
		this.deviceInfo = deviceInfo;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getFamilyId() {
		return familyId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public void revoke() {
		if (this.revokedAt == null) {
			this.revokedAt = Instant.now();
		}
	}
}
