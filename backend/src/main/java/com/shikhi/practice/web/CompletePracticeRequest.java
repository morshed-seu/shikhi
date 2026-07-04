package com.shikhi.practice.web;

import jakarta.validation.constraints.NotBlank;

/** Contract {@code IdempotentRequest} for completing a practice session. */
public record CompletePracticeRequest(@NotBlank String idempotencyKey) {
}
