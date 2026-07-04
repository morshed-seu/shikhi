package com.shikhi.practice.repo;

import com.shikhi.practice.domain.PracticeExercise;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeExerciseRepository extends JpaRepository<PracticeExercise, UUID> {

	List<PracticeExercise> findBySessionIdAndRoundOrderByOrdinal(UUID sessionId, int round);

	/** Vocabulary already used in this session — excluded from later rounds (no repeats). */
	List<PracticeExercise> findBySessionId(UUID sessionId);
}
