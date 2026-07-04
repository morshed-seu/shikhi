package com.shikhi.practice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One continuous practice run (E12): rounds of generated exercises keep coming until the
 * learner finishes. The CEFR level is pinned at start so a mid-session level change does
 * not reshuffle an active session (mirrors F4 version pinning for lessons).
 */
@Entity
@Table(name = "practice_sessions")
public class PracticeSession {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Column(name = "cefr_level", nullable = false)
	private String cefrLevel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PracticeStatus status = PracticeStatus.IN_PROGRESS;

	@Column(name = "rounds_played", nullable = false)
	private int roundsPlayed;

	@Column(name = "correct_count", nullable = false)
	private int correctCount;

	@Column(name = "total_count", nullable = false)
	private int totalCount;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt = Instant.now();

	@Column(name = "completed_at")
	private Instant completedAt;

	protected PracticeSession() {
		// for JPA
	}

	public PracticeSession(UUID userId, String cefrLevel) {
		this.userId = userId;
		this.cefrLevel = cefrLevel;
	}

	public void recordAnswer(boolean correct) {
		totalCount++;
		if (correct) {
			correctCount++;
		}
	}

	public void startRound() {
		roundsPlayed++;
	}

	public void complete() {
		this.status = PracticeStatus.COMPLETED;
		this.completedAt = Instant.now();
	}

	public boolean isCompleted() {
		return status == PracticeStatus.COMPLETED;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public String getCefrLevel() {
		return cefrLevel;
	}

	public PracticeStatus getStatus() {
		return status;
	}

	public int getRoundsPlayed() {
		return roundsPlayed;
	}

	public int getCorrectCount() {
		return correctCount;
	}

	public int getTotalCount() {
		return totalCount;
	}
}
