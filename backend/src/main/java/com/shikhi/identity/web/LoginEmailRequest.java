package com.shikhi.identity.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /auth/login} (contract {@code LoginEmailRequest}). */
public record LoginEmailRequest(
		@NotBlank @Email String email,
		@NotBlank String password) {
}
