package com.shikhi.practice.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * VE2 only needs load-by-key (graduation check, promote/demote in {@code WordProgressService}).
 * VE3 extends this with the due/backlog queries the daily planner's REVIEW bucket needs.
 */
public interface ReviewProgressRepository
		extends JpaRepository<ReviewProgress, ReviewProgress.Key> {
}
