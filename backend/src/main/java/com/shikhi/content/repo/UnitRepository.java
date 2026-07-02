package com.shikhi.content.repo;

import com.shikhi.content.domain.Unit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitRepository extends JpaRepository<Unit, UUID> {

	List<Unit> findByLevelIdOrderByOrdinal(UUID levelId);
}
