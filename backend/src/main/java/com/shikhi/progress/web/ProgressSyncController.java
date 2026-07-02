package com.shikhi.progress.web;

import com.shikhi.identity.security.AuthenticatedUser;
import com.shikhi.progress.service.ProgressSyncService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Offline reconciliation (contract {@code POST /progress/sync}). */
@RestController
@RequestMapping("/v1/progress")
public class ProgressSyncController {

	private final ProgressSyncService sync;

	public ProgressSyncController(ProgressSyncService sync) {
		this.sync = sync;
	}

	@PostMapping("/sync")
	public Stats sync(@AuthenticationPrincipal AuthenticatedUser principal,
			@Valid @RequestBody SyncBatchRequest request) {
		return sync.syncBatch(principal.id(), request);
	}
}
