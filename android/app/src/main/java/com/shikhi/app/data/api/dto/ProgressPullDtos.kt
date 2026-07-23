package com.shikhi.app.data.api.dto

import kotlinx.serialization.Serializable

// UO6 (docs/95-unified-offline-online-design.md §3.2/§5): wire shapes for the download direction
// of sync, GET /v1/progress/snapshot (built on the backend in UO5 — mirrors
// backend/src/main/java/com/shikhi/progress/web/ProgressSnapshotResponse.java field-for-field).
// Instant fields are ISO-8601 String on the wire, matching this codebase's existing convention
// (see LearningDtos.kt/PracticeDtos.kt — Stats et al. never use java.time.Instant directly).

@Serializable
data class WordProgressEntry(
	val vocabularyId: String,
	val timesSeen: Int,
	val timesCorrect: Int,
	val timesWrong: Int,
	val masteryScore: Int,
	val lastWrongAt: String? = null,
	val lastSeenAt: String,
)

@Serializable
data class ReviewProgressEntry(
	val vocabularyId: String,
	val reviewStage: Int,
	val dueAt: String,
	val lastReviewedAt: String? = null,
	val reviewCount: Int,
	val successfulReviews: Int,
	val failedReviews: Int,
	val failureStreak: Int,
	val lastFailureAt: String? = null,
)

@Serializable
data class CompletedLessonEntry(
	val lessonId: String,
	val contentVersionId: String,
	val score: Int,
)

@Serializable
data class ProgressSnapshotResponse(
	val stats: Stats,
	val wordProgress: List<WordProgressEntry> = emptyList(),
	val reviewProgress: List<ReviewProgressEntry> = emptyList(),
	val completedLessons: List<CompletedLessonEntry> = emptyList(),
	val serverTime: String,
)
