package com.shikhi.identity.web;

import com.shikhi.identity.domain.Identity;
import com.shikhi.identity.domain.User;
import java.time.Instant;
import java.util.List;

/**
 * A user's own data export (contract {@code GET /me/export}, NFR-PR2). Because this is the
 * subject's own data, identity references are returned in full (unmasked).
 */
public record AccountExport(UserResponse profile, List<ExportedIdentity> identities,
		Instant exportedAt) {

	public record ExportedIdentity(String provider, String reference, boolean verified) {
	}

	public static AccountExport of(User user, List<Identity> identities) {
		List<ExportedIdentity> exported = identities.stream()
				.map(i -> new ExportedIdentity(i.getProvider().name(), i.getExternalRef(),
						i.isVerified()))
				.toList();
		return new AccountExport(UserResponse.from(user), exported, Instant.now());
	}
}
