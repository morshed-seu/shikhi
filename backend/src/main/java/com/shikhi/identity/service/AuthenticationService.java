package com.shikhi.identity.service;

import com.shikhi.identity.domain.Credential;
import com.shikhi.identity.domain.Identity;
import com.shikhi.identity.domain.Locale;
import com.shikhi.identity.domain.Provider;
import com.shikhi.identity.domain.Role;
import com.shikhi.identity.domain.User;
import com.shikhi.identity.domain.UserStatus;
import com.shikhi.identity.repo.CredentialRepository;
import com.shikhi.identity.repo.IdentityRepository;
import com.shikhi.identity.repo.UserRepository;
import com.shikhi.identity.security.JwtService;
import com.shikhi.platform.error.ApiException;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email+password sign-in flows (M1 of D5). Structured so additional {@link Provider}s
 * (phone OTP, Google) slot in behind the same token-issuing seam without changing callers.
 */
@Service
public class AuthenticationService {

	private static final String INVALID_CREDENTIALS = "Invalid email or password";
	private static final String PASSWORD_ALGO = "argon2id";

	private final UserRepository users;
	private final IdentityRepository identities;
	private final CredentialRepository credentials;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokens;

	public AuthenticationService(UserRepository users, IdentityRepository identities,
			CredentialRepository credentials, PasswordEncoder passwordEncoder,
			JwtService jwtService, RefreshTokenService refreshTokens) {
		this.users = users;
		this.identities = identities;
		this.credentials = credentials;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.refreshTokens = refreshTokens;
	}

	@Transactional
	public TokenPair register(String email, String rawPassword, String displayName,
			Locale uiLocale) {
		String normalized = normalizeEmail(email);
		if (identities.existsByProviderAndExternalRef(Provider.EMAIL, normalized)) {
			throw ApiException.conflict("EMAIL_ALREADY_REGISTERED",
					"An account with this email already exists");
		}

		User user = new User(trimToNull(displayName), uiLocale);
		user.addRole(Role.LEARNER);
		users.save(user);

		identities.save(new Identity(user.getId(), Provider.EMAIL, normalized));
		credentials.save(new Credential(user.getId(), passwordEncoder.encode(rawPassword),
				PASSWORD_ALGO));

		return issueTokens(user);
	}

	@Transactional
	public TokenPair login(String email, String rawPassword) {
		String normalized = normalizeEmail(email);
		Identity identity = identities.findByProviderAndExternalRef(Provider.EMAIL, normalized)
				.orElseThrow(() -> ApiException.unauthorized(INVALID_CREDENTIALS));
		Credential credential = credentials.findByUserId(identity.getUserId())
				.orElseThrow(() -> ApiException.unauthorized(INVALID_CREDENTIALS));

		if (!passwordEncoder.matches(rawPassword, credential.getPasswordHash())) {
			throw ApiException.unauthorized(INVALID_CREDENTIALS);
		}

		User user = users.findById(identity.getUserId())
				.orElseThrow(() -> ApiException.unauthorized(INVALID_CREDENTIALS));
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw ApiException.unauthorized("Account is not active");
		}
		return issueTokens(user);
	}

	@Transactional
	public TokenPair refresh(String rawRefreshToken) {
		RefreshTokenService.RotationResult rotation = refreshTokens.rotate(rawRefreshToken);
		User user = users.findById(rotation.userId())
				.orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
		String access = jwtService.issueAccessToken(user.getId(), user.getRoles());
		return new TokenPair(access, rotation.newRawToken(), jwtService.accessTtlSeconds());
	}

	@Transactional
	public void logout(UUID userId) {
		refreshTokens.revokeAllForUser(userId);
	}

	private TokenPair issueTokens(User user) {
		String access = jwtService.issueAccessToken(user.getId(), user.getRoles());
		String refresh = refreshTokens.issue(user.getId(), UUID.randomUUID(), null);
		return new TokenPair(access, refresh, jwtService.accessTtlSeconds());
	}

	private static String normalizeEmail(String email) {
		return email == null ? null : email.trim().toLowerCase();
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
