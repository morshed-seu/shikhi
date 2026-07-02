package com.shikhi.content.web;

import com.shikhi.content.domain.ContentVersion;
import java.time.Instant;
import java.util.UUID;

/** Content version summary (contract {@code ContentVersion}). */
public record ContentVersionResponse(UUID id, String label, String status,
		Instant publishedAt) {

	public static ContentVersionResponse from(ContentVersion v) {
		return new ContentVersionResponse(v.getId(), v.getLabel(), v.getStatus().name(),
				v.getPublishedAt());
	}
}
