package com.shikhi.practice.schedule;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * VE2 only needs load-by-key (graduation check, promote/demote in {@code WordProgressService}).
 * VE3 extends this with the due/backlog queries the daily planner's REVIEW bucket needs.
 */
public interface ReviewProgressRepository
		extends JpaRepository<ReviewProgress, ReviewProgress.Key> {

	/** Every word this learner has graduated onto the review ladder (UO5 snapshot). */
	List<ReviewProgress> findByKey_UserId(UUID userId);

	/** Same as {@link #findByKey_UserId}, narrowed to rows touched strictly after {@code since}
	 * (UO5 {@code ?since=} incremental pull). */
	List<ReviewProgress> findByKey_UserIdAndUpdatedAtAfter(UUID userId, Instant since);
}
