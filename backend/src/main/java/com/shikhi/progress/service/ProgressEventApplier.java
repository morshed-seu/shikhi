package com.shikhi.progress.service;

import com.shikhi.practice.service.WordProgressService;
import com.shikhi.progress.domain.ProcessedEvent;
import com.shikhi.progress.repo.ProcessedEventRepository;
import com.shikhi.progress.web.SyncEvent;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
	private final WordProgressService wordProgressService;

	public ProgressEventApplier(ProcessedEventRepository processed, ProgressService progress,
			WordProgressService wordProgressService) {
		this.processed = processed;
		this.progress = progress;
		this.wordProgressService = wordProgressService;
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
			case "PRACTICE_ANSWER" -> practiceAnswer(userId, payload);
			case "SET_LEVEL" -> setLevel(userId, payload);
			default -> {
				// Unknown event type: ignore its effect but still mark it processed.
			}
		}
	}

	/**
	 * Offline vocabulary-practice answer (OF4/OF5, doc 93 §5). Unlike {@code "ANSWER"} (hearts
	 * only), this awards XP/hearts via {@link ProgressService#recordPracticeAnswer} AND advances
	 * mastery/review-ladder state via {@link WordProgressService#recordAnswer} — both, not just
	 * one, matching the online {@code PracticeSessionService.submitAnswer} path. The optional
	 * {@code answeredAt} avoids stamping a multi-day-old offline session's review-ladder
	 * {@code dueAt} as sync-time "now" (doc 93 §9 risk 1).
	 */
	private void practiceAnswer(UUID userId, Map<String, Object> payload) {
		UUID vocabularyId = uuidOf(payload.get("vocabularyId"));
		if (vocabularyId == null) {
			return;
		}
		boolean correct = boolOf(payload.get("correct"));
		progress.recordPracticeAnswer(userId, correct);
		wordProgressService.recordAnswer(userId, vocabularyId, correct,
				instantOf(payload.get("answeredAt")));
	}

	/** Offline CEFR level change (UO1); {@code changedAt} lets {@link ProgressService#setLevel}
	 * apply last-write-wins instead of blindly overwriting a newer online change. */
	private void setLevel(UUID userId, Map<String, Object> payload) {
		Object cefrLevel = payload.get("cefrLevel");
		if (cefrLevel == null) {
			return;
		}
		progress.setLevel(userId, cefrLevel.toString(), instantOf(payload.get("changedAt")));
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

	/**
	 * Parses an optional ISO-8601 instant string. Returns {@code null} (not a thrown exception)
	 * when absent or malformed, so a bad client timestamp can't crash sync — same defensive
	 * spirit as {@link #uuidOf}/{@link #boolOf}/{@link #intOf}. {@code null} tells
	 * {@link WordProgressService#recordAnswer(UUID, UUID, boolean, Instant)} to fall back to
	 * {@code clock.instant()}.
	 */
	private Instant instantOf(Object value) {
		try {
			return value == null ? null : Instant.parse(value.toString());
		}
		catch (DateTimeParseException ex) {
			return null;
		}
	}
}
