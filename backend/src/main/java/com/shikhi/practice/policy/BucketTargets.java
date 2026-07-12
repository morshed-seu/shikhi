package com.shikhi.practice.policy;

/**
 * How many words each bucket should contribute to today's plan (doc 42 §6.3/§6.6), after
 * {@link AllocationPolicy}'s percentage split and {@link RedistributionPolicy}'s
 * candidate-availability capping. The three counts may sum to less than the daily capacity —
 * the planner never invents items a bucket doesn't have.
 */
public record BucketTargets(int newTarget, int weakTarget, int reviewTarget) {
}
