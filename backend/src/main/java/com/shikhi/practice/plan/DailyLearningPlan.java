package com.shikhi.practice.plan;

import com.shikhi.practice.policy.Bucket;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One learner's workload for one day (doc 42 §6.1/§9.4, doc 43 §3 VE3): how many New/Weak/
 * Review words were planned, and how many are still left to serve. {@code UNIQUE(user_id,
 * plan_date)} (V22) backs idempotent creation in {@code DailyPlanService.getOrCreateToday};
 * {@code version} is JPA optimistic locking so two devices completing items concurrently
 * can't silently overwrite each other's {@code remaining*} decrements (doc 42 §6.7/§9.9).
 *
 * <p>Eagerly materialized (doc 43 deviation #3): every {@link DailyLearningPlanItem} for the
 * day is persisted at creation time, not lazily per session — acceptable at this scale
 * (~100 rows/learner/day).
 */
@Entity
@Table(name = "daily_learning_plans")
public class DailyLearningPlan {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "plan_date", nullable = false)
	private LocalDate planDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PlanStatus status;

	@Column(name = "daily_capacity", nullable = false)
	private int dailyCapacity;

	@Column(name = "planned_new", nullable = false)
	private int plannedNew;

	@Column(name = "planned_weak", nullable = false)
	private int plannedWeak;

	@Column(name = "planned_review", nullable = false)
	private int plannedReview;

	@Column(name = "remaining_new", nullable = false)
	private int remainingNew;

	@Column(name = "remaining_weak", nullable = false)
	private int remainingWeak;

	@Column(name = "remaining_review", nullable = false)
	private int remainingReview;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "config_snapshot")
	private Map<String, Object> configSnapshot;

	@Version
	@Column(nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "completed_at")
	private Instant completedAt;

	protected DailyLearningPlan() {
		// for JPA
	}

	public DailyLearningPlan(UUID userId, LocalDate planDate, int dailyCapacity, int plannedNew,
			int plannedWeak, int plannedReview, Map<String, Object> configSnapshot) {
		this.userId = userId;
		this.planDate = planDate;
		this.status = PlanStatus.ACTIVE;
		this.dailyCapacity = dailyCapacity;
		this.plannedNew = plannedNew;
		this.plannedWeak = plannedWeak;
		this.plannedReview = plannedReview;
		this.remainingNew = plannedNew;
		this.remainingWeak = plannedWeak;
		this.remainingReview = plannedReview;
		this.configSnapshot = configSnapshot;
	}

	/**
	 * Marks one word from {@code bucket} as served (VE4: called when a plan item is consumed
	 * by a session). Floors at zero rather than throwing — a stale client retry must not corrupt
	 * the header even if it races slightly ahead of the item-level state.
	 */
	public void consume(Bucket bucket) {
		switch (bucket) {
			case NEW -> remainingNew = Math.max(0, remainingNew - 1);
			case WEAK -> remainingWeak = Math.max(0, remainingWeak - 1);
			case REVIEW -> remainingReview = Math.max(0, remainingReview - 1);
		}
	}

	/**
	 * VE4: called once every {@code remaining*} counter has hit zero — today's word budget has
	 * been fully served by {@code PlanRoundComposer}. Idempotent: a caller re-checking an
	 * already-completed plan is a no-op rather than clobbering the original
	 * {@code completedAt}.
	 */
	public void complete(Instant now) {
		if (status == PlanStatus.COMPLETED) {
			return;
		}
		status = PlanStatus.COMPLETED;
		completedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public LocalDate getPlanDate() {
		return planDate;
	}

	public PlanStatus getStatus() {
		return status;
	}

	public int getDailyCapacity() {
		return dailyCapacity;
	}

	public int getPlannedNew() {
		return plannedNew;
	}

	public int getPlannedWeak() {
		return plannedWeak;
	}

	public int getPlannedReview() {
		return plannedReview;
	}

	public int getRemainingNew() {
		return remainingNew;
	}

	public int getRemainingWeak() {
		return remainingWeak;
	}

	public int getRemainingReview() {
		return remainingReview;
	}

	public Map<String, Object> getConfigSnapshot() {
		return configSnapshot;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}
}
