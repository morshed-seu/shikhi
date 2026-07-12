package com.shikhi.practice.policy;

/**
 * A New/Weak/Review split expressed as percentages of the daily capacity (doc 42 §6.2/§6.4).
 * Two instances flow through {@link AllocationPolicy}: the configured default (60/25/15) and
 * the backlog-protection override (70/25/5) used when due reviews exceed capacity.
 */
public record Percents(int newPercent, int weakPercent, int reviewPercent) {
}
