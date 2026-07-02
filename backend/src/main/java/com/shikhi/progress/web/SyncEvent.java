package com.shikhi.progress.web;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * One buffered offline event (contract {@code SyncBatchRequest.events[]}). {@code type} is
 * {@code ANSWER} or {@code COMPLETE_LESSON}; {@code payload} carries type-specific fields,
 * e.g. {@code {lessonId, contentVersionId, score}} for a completion. The {@code idempotencyKey}
 * makes each event apply at most once across devices.
 */
public record SyncEvent(@NotBlank String idempotencyKey, @NotBlank String type,
		Map<String, Object> payload) {
}
