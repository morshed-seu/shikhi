package com.shikhi.practice.web;

import com.shikhi.identity.security.AuthenticatedUser;
import com.shikhi.practice.service.PracticeSessionService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adaptive practice endpoints (contract {@code /practice/sessions/*}, E12). All require an
 * authenticated learner; a session can only be driven by the learner who started it.
 */
@RestController
@RequestMapping("/v1/practice/sessions")
public class PracticeController {

	private final PracticeSessionService sessions;

	public PracticeController(PracticeSessionService sessions) {
		this.sessions = sessions;
	}

	/** Start a session at my CEFR level; the response carries round 1. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PracticeRoundResponse start(@AuthenticationPrincipal AuthenticatedUser principal) {
		return sessions.start(principal.id());
	}

	@PostMapping("/{sessionId}/answers")
	public PracticeAnswerResult submitAnswer(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID sessionId, @Valid @RequestBody SubmitPracticeAnswerRequest request) {
		return sessions.submitAnswer(principal.id(), sessionId, request);
	}

	/** Batch submit (web client grades locally, then submits a round in one call). */
	@PostMapping("/{sessionId}/answers/batch")
	public PracticeBatchResult submitAnswers(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID sessionId, @Valid @RequestBody SubmitPracticeAnswersRequest request) {
		return sessions.submitAnswers(principal.id(), sessionId, request);
	}

	/** "Keep going" — generate the next round for this session. */
	@PostMapping("/{sessionId}/rounds")
	public PracticeRoundResponse nextRound(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID sessionId) {
		return sessions.nextRound(principal.id(), sessionId);
	}

	@PostMapping("/{sessionId}/complete")
	public PracticeResultResponse complete(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID sessionId,
			@Valid @RequestBody CompletePracticeRequest request) {
		return sessions.complete(principal.id(), sessionId);
	}
}
