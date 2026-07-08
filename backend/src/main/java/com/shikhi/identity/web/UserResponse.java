package com.shikhi.identity.web;

import com.shikhi.identity.domain.Locale;
import com.shikhi.identity.domain.Role;
import com.shikhi.identity.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public profile view (contract {@code User}, incl. {@code joinedAt} — E13). */
public record UserResponse(UUID id, String displayName, Locale uiLocale, List<String> roles,
		boolean isGuest, Instant joinedAt) {

	public static UserResponse from(User user) {
		List<String> roles = user.getRoles().stream().map(Role::name).sorted().toList();
		return new UserResponse(user.getId(), user.getDisplayName(), user.getUiLocale(), roles,
				user.isAnonymous(), user.getCreatedAt());
	}
}
