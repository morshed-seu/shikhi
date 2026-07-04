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
 * One learner's strength on one vocabulary word (E12, US-12.6). Strength runs 0..5: correct
 * answers add 1, wrong answers subtract 2, and unseen words (no row) count as 2 — so missed
 * words resurface first, unseen words next, and mastered words fade out of rotation.
 */
@Entity
@Table(name = "practice_word_progress")
public class PracticeWordProgress {

	/** Baseline strength assumed for words with no row yet (must match the picker's COALESCE). */
	public static final int UNSEEN_STRENGTH = 2;
	public static final int MAX_STRENGTH = 5;

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

	@Column(nullable = false)
	private int strength = UNSEEN_STRENGTH;

	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt = Instant.now();

	protected PracticeWordProgress() {
		// for JPA
	}

	public PracticeWordProgress(UUID userId, UUID vocabularyId) {
		this.key = new Key(userId, vocabularyId);
	}

	public void recordAnswer(boolean correct) {
		timesSeen++;
		if (correct) {
			timesCorrect++;
		}
		strength = Math.max(0, Math.min(MAX_STRENGTH, strength + (correct ? 1 : -2)));
		lastSeenAt = Instant.now();
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

	public int getStrength() {
		return strength;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}
}
