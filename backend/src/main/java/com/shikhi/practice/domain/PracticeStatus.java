package com.shikhi.practice.domain;

/** Lifecycle of a practice session. Kept local so practice does not depend on learning. */
public enum PracticeStatus {
	IN_PROGRESS,
	COMPLETED,
	ABANDONED
}
