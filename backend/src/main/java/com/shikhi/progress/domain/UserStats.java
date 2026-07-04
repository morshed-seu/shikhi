package com.shikhi.progress.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One learner's gamification state (LLD §3.3). Created lazily on first activity. The
 * day-rollover ({@link #registerActiveDay}) drives both the streak and a daily hearts
 * refill; hearts are otherwise spent on wrong answers. Per-user timezone is a later
 * refinement — the caller passes the day to keep this entity pure and unit-testable.
 */
@Entity
@Table(name = "user_stats")
public class UserStats {

	public static final int MAX_HEARTS = 5;
	public static final int DEFAULT_DAILY_GOAL = 20;

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(nullable = false)
	private int xp;

	@Column(nullable = false)
	private int rank;

	@Column(name = "current_streak", nullable = false)
	private int currentStreak;

	@Column(name = "longest_streak", nullable = false)
	private int longestStreak;

	@Column(name = "last_active_date")
	private LocalDate lastActiveDate;

	@Column(nullable = false)
	private int hearts = MAX_HEARTS;

	@Column(name = "hearts_updated_at", nullable = false)
	private Instant heartsUpdatedAt = Instant.now();

	@Column(name = "daily_goal", nullable = false)
	private int dailyGoal = DEFAULT_DAILY_GOAL;

	/** Self-placed / confirmed CEFR band (E12); drives practice word selection. */
	@Column(name = "cefr_level", nullable = false)
	private String cefrLevel = "A1";

	protected UserStats() {
		// for JPA
	}

	public UserStats(UUID userId) {
		this.userId = userId;
	}

	/**
	 * Mark the learner active on {@code today}. The first activity of a new day advances the
	 * streak (or resets it to 1 if a day was missed) and refills hearts. Idempotent within a
	 * day, so it is safe to call on every answer and on completion.
	 */
	public void registerActiveDay(LocalDate today) {
		if (today.equals(lastActiveDate)) {
			return;
		}
		if (lastActiveDate != null && lastActiveDate.plusDays(1).equals(today)) {
			currentStreak++;
		}
		else {
			currentStreak = 1;
		}
		longestStreak = Math.max(longestStreak, currentStreak);
		lastActiveDate = today;
		hearts = MAX_HEARTS;
		heartsUpdatedAt = Instant.now();
	}

	public void loseHeart() {
		if (hearts > 0) {
			hearts--;
			heartsUpdatedAt = Instant.now();
		}
	}

	public void addXp(int amount) {
		xp += amount;
	}

	public UUID getUserId() {
		return userId;
	}

	public int getXp() {
		return xp;
	}

	public int getRank() {
		return rank;
	}

	public int getCurrentStreak() {
		return currentStreak;
	}

	public int getLongestStreak() {
		return longestStreak;
	}

	public LocalDate getLastActiveDate() {
		return lastActiveDate;
	}

	public int getHearts() {
		return hearts;
	}

	public int getDailyGoal() {
		return dailyGoal;
	}

	public String getCefrLevel() {
		return cefrLevel;
	}

	public void setCefrLevel(String cefrLevel) {
		this.cefrLevel = cefrLevel;
	}
}
