package com.shikhi.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Password material for the EMAIL provider. Stores only a hash, never plaintext. */
@Entity
@Table(name = "credentials")
public class Credential {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "user_id", nullable = false, unique = true)
	private UUID userId;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(nullable = false)
	private String algo;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	protected Credential() {
		// for JPA
	}

	public Credential(UUID userId, String passwordHash, String algo) {
		this.userId = userId;
		this.passwordHash = passwordHash;
		this.algo = algo;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getUserId() {
		return userId;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getAlgo() {
		return algo;
	}

	public void setAlgo(String algo) {
		this.algo = algo;
	}
}
