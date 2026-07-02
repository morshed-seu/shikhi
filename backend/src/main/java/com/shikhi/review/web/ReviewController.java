package com.shikhi.review.web;

import com.shikhi.identity.security.AuthenticatedUser;
import com.shikhi.review.service.ReviewService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Spaced-repetition review (contract {@code /review/due}, {@code /review/results}). */
@RestController
@RequestMapping("/v1/review")
public class ReviewController {

	private final ReviewService review;

	public ReviewController(ReviewService review) {
		this.review = review;
	}

	@GetMapping("/due")
	public List<ReviewItemView> due(@AuthenticationPrincipal AuthenticatedUser principal) {
		return review.getDue(principal.id());
	}

	@PostMapping("/results")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void results(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody ReviewResultsRequest request) {
		review.recordResults(principal.id(), request.results());
	}
}
