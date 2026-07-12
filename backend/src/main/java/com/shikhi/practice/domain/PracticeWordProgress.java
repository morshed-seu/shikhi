package com.shikhi.practice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One learner's mastery of one vocabulary word (E12, US-12.6; doc 43 §3). masteryScore runs
 * 0..5: correct answers add 1, wrong answers subtract 2, and unseen words (no row) count as
 * 2 — so missed words resurface first, unseen words next, and mastered words fade out of
 * rotation. timesWrong/lastWrongAt additionally feed the weak-word "recent mistake" bonus
 * once a word graduates into word-level review (doc 43 §3 weak priority).
 */
@Entity
@Table(name = "practice_word_progress")
public class PracticeWordProgress {

	/** Baseline mastery assumed for words with no row yet (must match the picker's COALESCE). */
	public static final int UNSEEN_MASTERY = 2;
	public static final int MAX_MASTERY = 5;

	@Embeddable
	public record Key(@Column(name = "user_id") UUID userId,
			@Column(name = "vocabulary_id") UUID vocabularyId) implements Serializable {

		public Key {
			Objects.requireNonNull(userId);
			Objects.requireNonNull(vocabularyId);
		}
	}

	@EmbeddedId
	private Key key;

	@Column(name = "times_seen", nullable = false)
	private int timesSeen;

	@Column(name = "times_correct", nullable = false)
	private int timesCorrect;

	@Column(name = "mastery_score", nullable = false)
	private int masteryScore = UNSEEN_MASTERY;

	@Column(name = "times_wrong", nullable = false)
	private int timesWrong;

	@Column(name = "last_wrong_at")
	private Instant lastWrongAt;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt = Instant.now();

	protected PracticeWordProgress() {
		// for JPA
	}

	public PracticeWordProgress(UUID userId, UUID vocabularyId) {
		this.key = new Key(userId, vocabularyId);
	}

	/**
	 * {@code now} comes from the caller's {@link java.time.Clock} bean rather than
	 * {@link Instant#now()} (doc 43 §4 Fix 5): {@code WeakSelectionPolicy}'s 3-day recent-
	 * mistake window compares {@code lastWrongAt} against that same injected clock, so stamping
	 * with the wall clock here would silently diverge from it under a fixed/mutable test clock
	 * (or, in principle, from real client-perceived time in production).
	 */
	public void recordAnswer(boolean correct, Instant now) {
		timesSeen++;
		if (correct) {
			timesCorrect++;
		} else {
			timesWrong++;
			lastWrongAt = now;
		}
		masteryScore = Math.max(0, Math.min(MAX_MASTERY, masteryScore + (correct ? 1 : -2)));
		lastSeenAt = now;
	}

	public Key getKey() {
		return key;
	}

	public int getTimesSeen() {
		return timesSeen;
	}

	public int getTimesCorrect() {
		return timesCorrect;
	}

	public int getMasteryScore() {
		return masteryScore;
	}

	public int getTimesWrong() {
		return timesWrong;
	}

	public Instant getLastWrongAt() {
		return lastWrongAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}
}
