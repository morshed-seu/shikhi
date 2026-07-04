package com.shikhi.practice.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * Contract {@code SubmitAnswerRequest} for practice. The answer shape depends on the
 * exercise type: {@code {selectedOptionId}} (MCQ formats), {@code {tokenOrder}}
 * (SENTENCE_BUILD) or {@code {text}} (TYPE_WORD).
 */
public record SubmitPracticeAnswerRequest(
		@NotBlank String idempotencyKey,
		@NotNull UUID exerciseId,
		@NotNull Map<String, Object> answer) {
}
