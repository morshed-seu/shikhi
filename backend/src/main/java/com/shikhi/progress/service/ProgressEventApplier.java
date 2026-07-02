package com.shikhi.progress.service;

import com.shikhi.progress.domain.ProcessedEvent;
import com.shikhi.progress.repo.ProcessedEventRepository;
import com.shikhi.progress.web.SyncEvent;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a single sync event in its own transaction (REQUIRES_NEW) so one already-applied
 * event can't poison the rest of the batch. The idempotency check + ledger insert + the
 * effect all commit together, giving exactly-once semantics per key (LLD §7).
 */
@Component
public class ProgressEventApplier {

	private final ProcessedEventRepository processed;
	private final ProgressService progress;

	public ProgressEventApplier(ProcessedEventRepository processed, ProgressService progress) {
		this.processed = processed;
		this.progress = progress;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void applyIfNew(UUID userId, SyncEvent event) {
		if (event.idempotencyKey() == null
				|| processed.existsByUserIdAndIdempotencyKey(userId, event.idempotencyKey())) {
			return;
		}
		apply(userId, event);
		processed.save(new ProcessedEvent(userId, event.idempotencyKey()));
	}

	private void apply(UUID userId, SyncEvent event) {
		Map<String, Object> payload = event.payload() == null ? Map.of() : event.payload();
		switch (event.type()) {
			case "ANSWER" -> progress.recordAnswer(userId, boolOf(payload.get("correct")));
			case "COMPLETE_LESSON" -> completeLesson(userId, payload);
			default -> {
				// Unknown event type: ignore its effect but still mark it processed.
			}
		}
	}

	private void completeLesson(UUID userId, Map<String, Object> payload) {
		UUID lessonId = uuidOf(payload.get("lessonId"));
		if (lessonId == null) {
			return;
		}
		UUID versionId = uuidOf(payload.get("contentVersionId"));
		if (versionId == null) {
			versionId = progress.currentPublishedVersionId();
		}
		if (versionId == null) {
			return;
		}
		progress.completeLesson(userId, lessonId, versionId, intOf(payload.get("score")));
	}

	private boolean boolOf(Object value) {
		return value instanceof Boolean b && b;
	}

	private int intOf(Object value) {
		return value instanceof Number n ? n.intValue() : 0;
	}

	private UUID uuidOf(Object value) {
		try {
			return value == null ? null : UUID.fromString(value.toString());
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
