package com.shikhi.practice.policy;

/**
 * The three learning buckets a daily plan item can belong to (doc 42 §5). Lives in the policy
 * package — not {@code plan} — because it's the shared vocabulary the pure selection policies
 * and {@link BucketMixer} operate over; {@code plan.DailyLearningPlanItem} maps its
 * {@code bucket} column onto this same type rather than declaring a duplicate.
 */
public enum Bucket {
	NEW, WEAK, REVIEW
}
