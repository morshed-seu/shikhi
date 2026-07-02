package com.shikhi.identity.service;

import com.shikhi.identity.domain.Identity;
import com.shikhi.identity.domain.Locale;
import com.shikhi.identity.domain.User;
import com.shikhi.identity.repo.IdentityRepository;
import com.shikhi.identity.repo.UserRepository;
import com.shikhi.platform.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Profile reads/updates and identity listing for the authenticated user. */
@Service
public class AccountService {

	private final UserRepository users;
	private final IdentityRepository identities;

	public AccountService(UserRepository users, IdentityRepository identities) {
		this.users = users;
		this.identities = identities;
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
}
