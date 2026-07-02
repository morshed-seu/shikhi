package com.shikhi.learning.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * Contract {@code SubmitAnswerRequest}. {@code answer} is a type-specific payload, e.g.
 * {@code {selectedOptionId}} (MCQ), {@code {text}} (TYPE_TRANSLATION), {@code {tokenOrder}}
 * (WORD_BANK). {@code idempotencyKey} makes a resubmit safe.
 */
public record SubmitAnswerRequest(@NotBlank String idempotencyKey, @NotNull UUID exerciseId,
		@NotNull Map<String, Object> answer) {
}
