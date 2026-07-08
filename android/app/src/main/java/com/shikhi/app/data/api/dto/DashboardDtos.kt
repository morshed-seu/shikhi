package com.shikhi.app.data.api.dto

import kotlinx.serialization.Serializable

// Wire shapes mirror docs/43-api-contract.openapi.yaml DashboardResponse/WordMasteryEntry
// (E13) and frontend/src/api/dashboard.ts — one round-trip learner dashboard snapshot.

@Serializable
data class WordMasteryEntry(
	val cefrLevel: String = "A1",
	/** Words answered correctly at least once. */
	val mastered: Int = 0,
	/** Words in this band's vocabulary. */
	val total: Int = 0,
)

@Serializable
data class DashboardResponse(
	val stats: Stats = Stats(),
	/** One entry per CEFR band A1-C1, ordered. */
	val wordMastery: List<WordMasteryEntry> = emptyList(),
	val reviewDueCount: Int = 0,
	val lessonsCompleted: Int = 0,
	val practiceSessionsCompleted: Int = 0,
	/** Lifetime graded answers (lessons + practice). */
	val totalAnswered: Int = 0,
	val totalCorrect: Int = 0,
)
