package com.shikhi.practice.plan;

/**
 * Lifecycle of one learner's daily plan header (doc 42 §9.4). VE3 only ever creates
 * {@code ACTIVE} plans; {@code COMPLETED}/{@code EXPIRED}/{@code CANCELLED} are transitions
 * VE4's round composer and a future day-rollover job will drive.
 */
public enum PlanStatus {
	PENDING, ACTIVE, COMPLETED, EXPIRED, CANCELLED
}
