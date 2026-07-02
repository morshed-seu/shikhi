package com.shikhi.content.repo;

import com.shikhi.content.domain.ExerciseAnswer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseAnswerRepository extends JpaRepository<ExerciseAnswer, UUID> {

	List<ExerciseAnswer> findByExerciseId(UUID exerciseId);
}
