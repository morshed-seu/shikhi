package com.shikhi.practice.repo;

import com.shikhi.practice.domain.PracticeAnswer;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, UUID> {

	Optional<PracticeAnswer> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

	/**
	 * Rolling-accuracy inputs (doc 42 §6.4, doc 43 deviation #10) in one round trip instead of
	 * two separate {@code COUNT}s (doc 43 §4 Fix 7c): total answers this learner submitted since
	 * {@code since} (the planner passes {@code now - 7d}), and how many of those were correct,
	 * via Postgres's {@code count(*) filter (where ...)}.
	 */
	@Query(value = """
			select count(*) as totalCount, count(*) filter (where correct) as correctCount
			from practice_answers
			where user_id = :userId and submitted_at > :since
			""", nativeQuery = true)
	AccuracyCounts rollingAccuracyCounts(@Param("userId") UUID userId, @Param("since") Instant since);

	/** Projection for {@link #rollingAccuracyCounts}; column aliases map by relaxed name match. */
	interface AccuracyCounts {

		long getTotalCount();

		long getCorrectCount();
	}
}
