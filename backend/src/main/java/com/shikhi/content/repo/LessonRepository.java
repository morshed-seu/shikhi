package com.shikhi.content.repo;

import com.shikhi.content.domain.Lesson;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {

	List<Lesson> findByUnitIdOrderByOrdinal(UUID unitId);
}
