package com.shikhi.identity.web;

import com.shikhi.identity.domain.Locale;

/**
 * Body of {@code POST /auth/guest} (contract {@code GuestRequest}). Optional — the only field
 * is the preferred UI locale so the guest starts in the right language.
 */
public record GuestRequest(Locale uiLocale) {
}
