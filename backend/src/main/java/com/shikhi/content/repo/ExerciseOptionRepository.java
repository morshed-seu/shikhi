package com.shikhi.content.repo;

import com.shikhi.content.domain.ExerciseOption;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseOptionRepository extends JpaRepository<ExerciseOption, UUID> {

	List<ExerciseOption> findByExerciseIdOrderByOrdinal(UUID exerciseId);
}
