package com.shikhi.practice.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contract {@code SubmitAnswersRequest} for practice batch submission (E12 batch submit):
 * the web client grades locally, then submits every round answer in one call. The server
 * still re-grades each item authoritatively against the stored answer key.
 */
public record SubmitPracticeAnswersRequest(@NotEmpty @Valid List<BatchAnswerItem> answers) {

	/** One graded-locally answer, same shape as {@link SubmitPracticeAnswerRequest}. */
	public record BatchAnswerItem(
			@NotBlank String idempotencyKey,
			@NotNull UUID exerciseId,
			@NotNull Map<String, Object> answer) {
	}
}
