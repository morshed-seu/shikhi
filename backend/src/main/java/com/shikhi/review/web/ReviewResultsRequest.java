package com.shikhi.review.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Contract {@code ReviewResultsRequest} — outcomes of a review session. */
public record ReviewResultsRequest(@NotNull @Valid List<ReviewResult> results) {

	/** One recalled-or-not outcome for a review exercise. */
	public record ReviewResult(@NotNull UUID exerciseId, boolean correct) {
	}
}
