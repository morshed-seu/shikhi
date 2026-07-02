package com.shikhi.progress.repo;

import com.shikhi.progress.domain.UserStats;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {
}
