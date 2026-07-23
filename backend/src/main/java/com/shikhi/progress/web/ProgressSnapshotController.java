package com.shikhi.progress.web;

import com.shikhi.identity.security.AuthenticatedUser;
import com.shikhi.progress.service.ProgressSnapshotService;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authoritative per-user progress snapshot (contract {@code GET /progress/snapshot}, UO5) —
 * rebuilds local Android progress from server truth on a reinstall or a second device.
 * Read-only: no side effects. The optional {@code since} query param narrows the snapshot to
 * rows touched strictly after that instant, for a future incremental pull; omit it for the full
 * snapshot.
 */
@RestController
@RequestMapping("/v1/progress")
public class ProgressSnapshotController {

	private final ProgressSnapshotService snapshot;

	public ProgressSnapshotController(ProgressSnapshotService snapshot) {
		this.snapshot = snapshot;
	}

	@GetMapping("/snapshot")
	public ProgressSnapshotResponse snapshot(@AuthenticationPrincipal AuthenticatedUser principal,
			@RequestParam(required = false) Instant since) {
		return snapshot.snapshot(principal.id(), since);
	}
}
