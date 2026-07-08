package com.shikhi.review.repo;

import com.shikhi.review.domain.ReviewItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, UUID> {

	Optional<ReviewItem> findByUserIdAndExerciseId(UUID userId, UUID exerciseId);

	List<ReviewItem> findByUserIdAndDueAtLessThanEqualOrderByDueAt(UUID userId, Instant now);

	long countByUserIdAndUpdatedAtGreaterThanEqual(UUID userId, Instant since);

	long countByUserIdAndDueAtLessThanEqual(UUID userId, Instant now);
}
