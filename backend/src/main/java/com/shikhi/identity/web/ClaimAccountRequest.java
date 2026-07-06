package com.shikhi.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /auth/claim} (contract {@code ClaimAccountRequest}). The caller is an
 * authenticated guest; the user id comes from the token, not the body.
 */
public record ClaimAccountRequest(
		@NotBlank @Email String email,
		@NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
		String displayName) {
}
