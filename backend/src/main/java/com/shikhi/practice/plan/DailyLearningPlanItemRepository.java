package com.shikhi.practice.plan;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyLearningPlanItemRepository
		extends JpaRepository<DailyLearningPlanItem, UUID> {

	List<DailyLearningPlanItem> findByPlanIdAndStatusOrderBySequence(UUID planId,
			ItemStatus status);

	List<DailyLearningPlanItem> findByPlanIdOrderBySequence(UUID planId);
}
