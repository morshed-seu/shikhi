package com.shikhi.learning.web;

import com.shikhi.progress.web.Stats;

/** Contract {@code AnswerResult} — the graded verdict plus the learner's updated stats. */
public record AnswerResult(Verdict verdict, Stats stats) {
}
