package com.shikhi.learning.web;

/** Contract {@code AnswerResult} — the graded verdict plus the updated session stats. */
public record AnswerResult(Verdict verdict, Stats stats) {
}
