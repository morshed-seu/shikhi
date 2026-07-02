package com.shikhi.learning.domain;

/** Lifecycle of a single lesson play-through (contract {@code LessonSession.status}). */
public enum SessionStatus {
	IN_PROGRESS,
	COMPLETED,
	ABANDONED
}
