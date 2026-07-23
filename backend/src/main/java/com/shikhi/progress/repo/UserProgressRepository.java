package com.shikhi.progress.repo;

import com.shikhi.progress.domain.UserProgress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {

	List<UserProgress> findByUserIdAndContentVersionId(UUID userId, UUID contentVersionId);

	Optional<UserProgress> findByUserIdAndLessonIdAndContentVersionId(UUID userId, UUID lessonId,
			UUID contentVersionId);

	/** All of a learner's progress rows, across every lesson/content version (UO5 snapshot). */
	List<UserProgress> findByUserId(UUID userId);

	/** Same as {@link #findByUserId}, narrowed to rows completed strictly after {@code since}
	 * (UO5 {@code ?since=} incremental pull). */
	List<UserProgress> findByUserIdAndCompletedAtAfter(UUID userId, Instant since);
}
