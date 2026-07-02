package com.shikhi.identity.service;

import com.shikhi.identity.domain.Identity;
import com.shikhi.identity.domain.Locale;
import com.shikhi.identity.domain.User;
import com.shikhi.identity.domain.UserStatus;
import com.shikhi.identity.repo.CredentialRepository;
import com.shikhi.identity.repo.IdentityRepository;
import com.shikhi.identity.repo.UserRepository;
import com.shikhi.platform.error.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile reads/updates, identity listing, data export, and account deletion. */
@Service
public class AccountService {

	private final UserRepository users;
	private final IdentityRepository identities;
	private final CredentialRepository credentials;
	private final RefreshTokenService refreshTokens;

	public AccountService(UserRepository users, IdentityRepository identities,
			CredentialRepository credentials, RefreshTokenService refreshTokens) {
		this.users = users;
		this.identities = identities;
		this.credentials = credentials;
		this.refreshTokens = refreshTokens;
	}

	@Transactional(readOnly = true)
	public User getUser(UUID userId) {
		return users.findById(userId)
				.orElseThrow(() -> ApiException.notFound("User not found"));
	}

	@Transactional
	public User updateProfile(UUID userId, String displayName, Locale uiLocale) {
		User user = getUser(userId);
		if (displayName != null) {
			user.setDisplayName(displayName.isBlank() ? null : displayName.trim());
		}
		if (uiLocale != null) {
			user.setUiLocale(uiLocale);
		}
		return users.save(user);
	}

	@Transactional(readOnly = true)
	public List<Identity> listIdentities(UUID userId) {
		return identities.findByUserId(userId);
	}

	/**
	 * Delete/anonymize the account (NFR-PR2, deletion semantics in `50` §User rights).
	 * Soft-deletes the user row (keeps a non-identifying anchor for aggregate stats),
	 * removes PII-bearing identities and credentials, and revokes all sessions.
	 */
	@Transactional
	public void deleteAccount(UUID userId) {
		User user = getUser(userId);
		user.setDisplayName(null);
		user.setStatus(UserStatus.DELETED);
		user.setDeletedAt(Instant.now());
		users.save(user);

		credentials.findByUserId(userId).ifPresent(credentials::delete);
		identities.deleteAll(identities.findByUserId(userId));
		refreshTokens.revokeAllForUser(userId);
	}
}
