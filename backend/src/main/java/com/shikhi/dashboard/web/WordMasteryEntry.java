package com.shikhi.dashboard.web;

/** One CEFR band's word-mastery counts (contract {@code WordMasteryEntry}). */
public record WordMasteryEntry(String cefrLevel, int mastered, int total) {
}
