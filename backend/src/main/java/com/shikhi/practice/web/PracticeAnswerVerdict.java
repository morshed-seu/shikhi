package com.shikhi.practice.web;

import java.util.UUID;

/**
 * Contract {@code AnswerVerdict} — one exercise's server-graded correctness within a
 * {@link PracticeBatchResult}. No feedback text: the client already grades locally against
 * the {@code solution} served with the round.
 */
public record PracticeAnswerVerdict(UUID exerciseId, boolean correct) {
}
