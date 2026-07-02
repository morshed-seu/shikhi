package com.shikhi.learning.web;

import com.shikhi.identity.security.AuthenticatedUser;
import com.shikhi.learning.service.LessonSessionService;
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
 * Lesson play-through endpoints (contract {@code /sessions}, {@code /sessions/{id}/answers},
 * {@code /sessions/{id}/complete}). All require an authenticated learner; a session can only
 * be driven by the learner who started it.
 */
@RestController
@RequestMapping("/v1/sessions")
public class LessonSessionController {

	private final LessonSessionService sessions;

	public LessonSessionController(LessonSessionService sessions) {
		this.sessions = sessions;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public LessonSessionResponse start(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody StartSessionRequest request) {
		return sessions.start(principal.id(), request.lessonId());
	}

	@PostMapping("/{sessionId}/answers")
	public AnswerResult submitAnswer(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID sessionId, @Valid @RequestBody SubmitAnswerRequest request) {
		return sessions.submitAnswer(principal.id(), sessionId, request);
	}

	@PostMapping("/{sessionId}/complete")
	public LessonResult complete(@AuthenticationPrincipal AuthenticatedUser principal,
			@PathVariable UUID sessionId, @Valid @RequestBody CompleteSessionRequest request) {
		return sessions.complete(principal.id(), sessionId);
	}
}
