package com.shikhi.practice.repo;

import com.shikhi.practice.domain.PracticeWordProgress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticeWordProgressRepository
		extends JpaRepository<PracticeWordProgress, PracticeWordProgress.Key> {

	/**
	 * Distinct words of one CEFR band the learner has answered correctly at least once —
	 * the numerator of level-up eligibility (US-12.7).
	 */
	@Query(value = """
			select count(*) from practice_word_progress p
			join vocabulary v on v.id = p.vocabulary_id
			where p.user_id = :userId and v.cefr_level = :cefrLevel and p.times_correct > 0
			""", nativeQuery = true)
	long countMasteredInBand(@Param("userId") UUID userId, @Param("cefrLevel") String cefrLevel);

	/** Every word this learner has ever seen (UO5 snapshot). */
	List<PracticeWordProgress> findByKey_UserId(UUID userId);

	/** Same as {@link #findByKey_UserId}, narrowed to rows last seen strictly after
	 * {@code since} (UO5 {@code ?since=} incremental pull). */
	List<PracticeWordProgress> findByKey_UserIdAndLastSeenAtAfter(UUID userId, Instant since);
}
