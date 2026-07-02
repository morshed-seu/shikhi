package com.shikhi.identity.web;

import com.shikhi.identity.domain.Locale;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /auth/register} (contract {@code RegisterEmailRequest}). */
public record RegisterEmailRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
		String displayName,
		Locale uiLocale) {
}
