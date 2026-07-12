package com.shikhi.practice.plan;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyLearningPlanRepository extends JpaRepository<DailyLearningPlan, UUID> {

	Optional<DailyLearningPlan> findByUserIdAndPlanDate(UUID userId, LocalDate planDate);
}
