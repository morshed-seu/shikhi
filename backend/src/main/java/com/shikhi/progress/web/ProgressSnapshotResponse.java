package com.shikhi.progress.web;

import com.shikhi.practice.domain.PracticeWordProgress;
import com.shikhi.practice.schedule.ReviewProgress;
import com.shikhi.progress.domain.UserProgress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Contract {@code ProgressSnapshotResponse} (UO5, {@code GET /progress/snapshot}) — an
 * authoritative, read-only per-user snapshot used to rebuild local Android progress from server
 * truth (reinstall / a second device). {@code serverTime} is the client's future cursor for an
 * incremental {@code ?since=} pull.
 */
public record ProgressSnapshotResponse(Stats stats, List<WordProgressEntry> wordProgress,
		List<ReviewProgressEntry> reviewProgress, List<CompletedLessonEntry> completedLessons,
		Instant serverTime) {

	public record WordProgressEntry(UUID vocabularyId, int timesSeen, int timesCorrect,
			int timesWrong, int masteryScore, Instant lastWrongAt, Instant lastSeenAt) {

		public static WordProgressEntry from(PracticeWordProgress p) {
			return new WordProgressEntry(p.getKey().vocabularyId(), p.getTimesSeen(),
					p.getTimesCorrect(), p.getTimesWrong(), p.getMasteryScore(),
					p.getLastWrongAt(), p.getLastSeenAt());
		}
	}

	public record ReviewProgressEntry(UUID vocabularyId, int reviewStage, Instant dueAt,
			Instant lastReviewedAt, int reviewCount, int successfulReviews, int failedReviews,
			int failureStreak, Instant lastFailureAt) {

		public static ReviewProgressEntry from(ReviewProgress r) {
			return new ReviewProgressEntry(r.getKey().vocabularyId(), r.getReviewStage(),
					r.getDueAt(), r.getLastReviewedAt(), r.getReviewCount(),
					r.getSuccessfulReviews(), r.getFailedReviews(), r.getFailureStreak(),
					r.getLastFailureAt());
		}
	}

	public record CompletedLessonEntry(UUID lessonId, UUID contentVersionId, int score) {

		public static CompletedLessonEntry from(UserProgress p) {
			return new CompletedLessonEntry(p.getLessonId(), p.getContentVersionId(),
					p.getBestScore());
		}
	}
}
