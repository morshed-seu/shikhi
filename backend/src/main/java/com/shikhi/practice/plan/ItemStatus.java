package com.shikhi.practice.plan;

/**
 * Lifecycle of one {@link DailyLearningPlanItem} (doc 42 §9.5). VE3 only ever creates
 * {@code PENDING} items; VE4's round composer drives the rest as the plan is consumed.
 */
public enum ItemStatus {
	PENDING, IN_PROGRESS, COMPLETED, SKIPPED
}
