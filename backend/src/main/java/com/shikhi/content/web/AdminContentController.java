package com.shikhi.content.web;

import com.shikhi.content.service.AuthoringService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authoring endpoints (contract {@code /admin/content/*}). Role-gated to AUTHOR/ADMIN in
 * {@code SecurityConfig}; learners receive 403.
 */
@RestController
@RequestMapping("/v1/admin/content")
public class AdminContentController {

	private final AuthoringService authoring;

	public AdminContentController(AuthoringService authoring) {
		this.authoring = authoring;
	}

	@PostMapping("/drafts")
	@ResponseStatus(HttpStatus.CREATED)
	public ContentVersionResponse createDraft() {
		return authoring.createDraft();
	}

	@PostMapping("/drafts/{versionId}/validate")
	public ValidationResult validate(@PathVariable UUID versionId) {
		return authoring.validate(versionId);
	}

	@PostMapping("/drafts/{versionId}/publish")
	public ContentVersionResponse publish(@PathVariable UUID versionId) {
		return authoring.publish(versionId);
	}
}
