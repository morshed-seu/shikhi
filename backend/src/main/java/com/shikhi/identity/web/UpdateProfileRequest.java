package com.shikhi.identity.web;

import com.shikhi.identity.domain.Locale;

/** Body of {@code PATCH /me} (contract {@code UpdateProfileRequest}). Fields are optional. */
public record UpdateProfileRequest(String displayName, Locale uiLocale) {
}
