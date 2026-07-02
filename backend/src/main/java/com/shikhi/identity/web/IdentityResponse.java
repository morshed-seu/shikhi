package com.shikhi.identity.web;

import com.shikhi.identity.domain.Identity;
import com.shikhi.identity.domain.Provider;

/** Linked sign-in method view (contract {@code Identity}). The reference is masked for display. */
public record IdentityResponse(String provider, boolean verified, String maskedRef) {

	public static IdentityResponse from(Identity identity) {
		return new IdentityResponse(identity.getProvider().name(), identity.isVerified(),
				mask(identity.getProvider(), identity.getExternalRef()));
	}

	private static String mask(Provider provider, String ref) {
		if (ref == null || ref.isBlank()) {
			return null;
		}
		if (provider == Provider.EMAIL) {
			int at = ref.indexOf('@');
			if (at <= 0) {
				return "***";
			}
			String local = ref.substring(0, at);
			String domain = ref.substring(at);
			String head = local.substring(0, Math.min(2, local.length()));
			return head + "***" + domain;
		}
		// Phone/other: show only the last two characters.
		int keep = Math.min(2, ref.length());
		return "***" + ref.substring(ref.length() - keep);
	}
}
