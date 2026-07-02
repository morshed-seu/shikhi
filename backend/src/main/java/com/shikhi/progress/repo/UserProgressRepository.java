package com.shikhi.progress.repo;

import com.shikhi.progress.domain.UserProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProgressRepository extends JpaRepository<UserProgress, UUID> {

	List<UserProgress> findByUserIdAndContentVersionId(UUID userId, UUID contentVersionId);

	Optional<UserProgress> findByUserIdAndLessonIdAndContentVersionId(UUID userId, UUID lessonId,
			UUID contentVersionId);
}
