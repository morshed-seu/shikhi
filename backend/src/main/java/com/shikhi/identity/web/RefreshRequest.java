package com.shikhi.identity.web;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /auth/refresh} (contract {@code RefreshRequest}). */
public record RefreshRequest(@NotBlank String refreshToken) {
}
