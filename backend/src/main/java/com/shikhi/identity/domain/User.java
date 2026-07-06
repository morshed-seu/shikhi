package com.shikhi.identity.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** A learner account. Aggregate root of the identity module. */
@Entity
@Table(name = "users")
public class User {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "display_name")
	private String displayName;

	@Column(name = "ui_locale", nullable = false)
	private Locale uiLocale = Locale.BN;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status = UserStatus.ACTIVE;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private Set<Role> roles = EnumSet.noneOf(Role.class);

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected User() {
		// for JPA
	}

	public User(String displayName, Locale uiLocale) {
		this.displayName = displayName;
		if (uiLocale != null) {
			this.uiLocale = uiLocale;
		}
	}

	/**
	 * A guest learner: {@link UserStatus#ANONYMOUS}, no linked identity/credential. Carries a
	 * real {@code id} so all progress hangs off it; {@link #claim} upgrades it in place. Callers
	 * still add {@link Role#LEARNER} so the guest gets the full learning loop.
	 */
	public static User anonymous(Locale uiLocale) {
		User user = new User(null, uiLocale);
		user.status = UserStatus.ANONYMOUS;
		return user;
	}

	/**
	 * Upgrade an anonymous guest into a full account in place: keep the same {@code id} (so all
	 * progress stays owned by it) and flip to {@link UserStatus#ACTIVE}. The caller persists the
	 * new {@link Identity}/{@link Credential}.
	 */
	public void claim(String displayName) {
		if (displayName != null && !displayName.isBlank()) {
			this.displayName = displayName.trim();
		}
		this.status = UserStatus.ACTIVE;
	}

	public boolean isAnonymous() {
		return status == UserStatus.ANONYMOUS;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public void addRole(Role role) {
		this.roles.add(role);
	}

	public UUID getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public Locale getUiLocale() {
		return uiLocale;
	}

	public void setUiLocale(Locale uiLocale) {
		this.uiLocale = uiLocale;
	}

	public UserStatus getStatus() {
		return status;
	}

	public void setStatus(UserStatus status) {
		this.status = status;
	}

	public Set<Role> getRoles() {
		return roles;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}
}
