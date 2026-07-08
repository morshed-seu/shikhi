package com.shikhi.dashboard.web;

import com.shikhi.dashboard.service.DashboardService;
import com.shikhi.identity.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner dashboard (contract {@code GET /dashboard}; read-only, E13). */
@RestController
@RequestMapping("/v1/dashboard")
public class DashboardController {

	private final DashboardService dashboard;

	public DashboardController(DashboardService dashboard) {
		this.dashboard = dashboard;
	}

	@GetMapping
	public DashboardResponse snapshot(@AuthenticationPrincipal AuthenticatedUser principal) {
		return dashboard.snapshot(principal.id());
	}
}
