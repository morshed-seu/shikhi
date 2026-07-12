package com.shikhi.practice.plan;

import com.shikhi.practice.policy.Bucket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One word slotted into a {@link DailyLearningPlan}, in serve order (doc 42 §9.5). {@code
 * sequence} is the position {@link com.shikhi.practice.policy.BucketMixer} assigned at plan
 * creation; VE4's round composer takes pending items strictly by sequence, so the bucket mix
 * decided once at creation time is never recomputed mid-day. {@code UNIQUE(plan_id,
 * vocabulary_id)} (V22) is why {@code DailyPlanService} de-dupes a word that is both due for
 * review and would otherwise qualify as weak before this row is ever built.
 */
@Entity
@Table(name = "daily_learning_plan_items")
public class DailyLearningPlanItem {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "plan_id", nullable = false)
	private UUID planId;

	@Column(name = "vocabulary_id", nullable = false)
	private UUID vocabularyId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Bucket bucket;

	@Column(nullable = false)
	private int sequence;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ItemStatus status = ItemStatus.PENDING;

	@Column(name = "consumed_session_id")
	private UUID consumedSessionId;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	protected DailyLearningPlanItem() {
		// for JPA
	}

	public DailyLearningPlanItem(UUID planId, UUID vocabularyId, Bucket bucket, int sequence) {
		this.planId = planId;
		this.vocabularyId = vocabularyId;
		this.bucket = bucket;
		this.sequence = sequence;
	}

	/** VE4: a session actually served this word — stamp it done, once. */
	public void consume(UUID sessionId, Instant now) {
		this.status = ItemStatus.COMPLETED;
		this.consumedSessionId = sessionId;
		this.consumedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getPlanId() {
		return planId;
	}

	public UUID getVocabularyId() {
		return vocabularyId;
	}

	public Bucket getBucket() {
		return bucket;
	}

	public int getSequence() {
		return sequence;
	}

	public ItemStatus getStatus() {
		return status;
	}

	public UUID getConsumedSessionId() {
		return consumedSessionId;
	}

	public Instant getConsumedAt() {
		return consumedAt;
	}
}
