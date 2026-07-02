package com.shikhi.progress.web;

import com.shikhi.identity.security.AuthenticatedUser;
import com.shikhi.progress.service.ProgressService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner stats (contract {@code GET /stats}). */
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
}
