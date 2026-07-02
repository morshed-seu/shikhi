package com.shikhi.content.repo;

import com.shikhi.content.domain.Exercise;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExerciseRepository extends JpaRepository<Exercise, UUID> {

	List<Exercise> findByLessonIdOrderByOrdinal(UUID lessonId);
}
