package com.shikhi.progress.service;

import com.shikhi.progress.web.Stats;
import com.shikhi.progress.web.SyncBatchRequest;
import com.shikhi.progress.web.SyncEvent;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Reconciles a batch of buffered offline events (D2/NFR-N2). Each event is applied in its own
 * transaction via {@link ProgressEventApplier}, so a partial failure or an already-applied
 * event doesn't abort the whole batch. Returns the learner's fresh stats.
 */
@Service
public class ProgressSyncService {

	private final ProgressEventApplier applier;
	private final ProgressService progress;

	public ProgressSyncService(ProgressEventApplier applier, ProgressService progress) {
		this.applier = applier;
		this.progress = progress;
	}

	public Stats syncBatch(UUID userId, SyncBatchRequest request) {
		if (request.events() != null) {
			for (SyncEvent event : request.events()) {
				applier.applyIfNew(userId, event);
			}
		}
		return progress.getState(userId);
	}
}
