package com.shikhi.learning.web;

import jakarta.validation.constraints.NotBlank;

/** Contract {@code IdempotentRequest} for completing a session. */
public record CompleteSessionRequest(@NotBlank String idempotencyKey) {
}
