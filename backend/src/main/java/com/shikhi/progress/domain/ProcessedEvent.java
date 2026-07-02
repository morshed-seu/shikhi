package com.shikhi.progress.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * An idempotency ledger entry: a sync event this learner has already applied. The
 * UNIQUE(user_id, idempotency_key) constraint means a buffered event replayed from another
 * device is applied at most once (LLD §7).
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	protected ProcessedEvent() {
		// for JPA
	}

	public ProcessedEvent(UUID userId, String idempotencyKey) {
		this.userId = userId;
		this.idempotencyKey = idempotencyKey;
	}
}
