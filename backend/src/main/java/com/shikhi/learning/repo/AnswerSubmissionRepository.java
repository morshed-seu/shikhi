package com.shikhi.learning.repo;

import com.shikhi.learning.domain.AnswerSubmission;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerSubmissionRepository extends JpaRepository<AnswerSubmission, UUID> {

	Optional<AnswerSubmission> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
