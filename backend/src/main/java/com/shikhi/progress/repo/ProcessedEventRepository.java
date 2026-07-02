package com.shikhi.progress.repo;

import com.shikhi.progress.domain.ProcessedEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

	boolean existsByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
