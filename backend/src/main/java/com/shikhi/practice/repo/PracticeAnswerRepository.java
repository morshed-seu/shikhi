package com.shikhi.practice.repo;

import com.shikhi.practice.domain.PracticeAnswer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeAnswerRepository extends JpaRepository<PracticeAnswer, UUID> {

	Optional<PracticeAnswer> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
