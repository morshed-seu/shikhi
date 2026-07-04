package com.shikhi.progress.web;

import com.shikhi.identity.security.AuthenticatedUser;
import com.shikhi.progress.service.ProgressService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner stats (contract {@code GET /stats}, {@code PUT /stats/level}). */
@RestController
@RequestMapping("/v1/stats")
public class StatsController {

	private final ProgressService progress;

	public StatsController(ProgressService progress) {
		this.progress = progress;
	}

	@GetMapping
	public Stats stats(@AuthenticationPrincipal AuthenticatedUser principal) {
		return progress.getState(principal.id());
	}

	/** Set my CEFR band — onboarding self-placement or an accepted level-up (E12). */
	@PutMapping("/level")
	public Stats setLevel(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody SetLevelRequest request) {
		return progress.setLevel(principal.id(), request.cefrLevel());
	}
}
