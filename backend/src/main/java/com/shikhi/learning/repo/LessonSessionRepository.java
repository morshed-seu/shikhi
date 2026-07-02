package com.shikhi.learning.repo;

import com.shikhi.learning.domain.LessonSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonSessionRepository extends JpaRepository<LessonSession, UUID> {
}
