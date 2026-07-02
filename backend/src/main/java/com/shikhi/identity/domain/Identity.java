package com.shikhi.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One linked sign-in method for a user. {@code (provider, externalRef)} is globally unique,
 * so an email/phone/Google subject maps to exactly one account (D5).
 */
@Entity
@Table(name = "identities")
public class Identity {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Provider provider;

	@Column(name = "external_ref", nullable = false)
	private String externalRef;

	@Column(name = "verified_at")
	private Instant verifiedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Identity() {
		// for JPA
	}

	public Identity(UUID userId, Provider provider, String externalRef) {
		this.userId = userId;
		this.provider = provider;
		this.externalRef = externalRef;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public Provider getProvider() {
		return provider;
	}

	public String getExternalRef() {
		return externalRef;
	}

	public Instant getVerifiedAt() {
		return verifiedAt;
	}

	public void markVerified() {
		this.verifiedAt = Instant.now();
	}

	public boolean isVerified() {
		return verifiedAt != null;
	}
}
