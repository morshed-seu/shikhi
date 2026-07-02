package com.shikhi.progress.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Contract {@code SyncBatchRequest} — a batch of buffered offline events to reconcile. */
public record SyncBatchRequest(@NotNull @Valid List<SyncEvent> events) {
}
