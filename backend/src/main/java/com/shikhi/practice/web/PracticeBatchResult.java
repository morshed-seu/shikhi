package com.shikhi.practice.web;

import com.shikhi.progress.web.Stats;
import java.util.List;

/**
 * Contract {@code BatchResult} for practice batch submission: the final stats snapshot
 * after every answer in the batch was re-graded server-side, plus a per-exercise verdict.
 */
public record PracticeBatchResult(Stats stats, List<PracticeAnswerVerdict> verdicts) {
}
