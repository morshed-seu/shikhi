package com.shikhi.practice.web;

import com.shikhi.progress.web.Stats;

/** Contract {@code AnswerResult} for practice: the verdict plus the learner's fresh stats. */
public record PracticeAnswerResult(PracticeVerdict verdict, Stats stats) {
}
