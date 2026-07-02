package com.shikhi.content.repo;

import com.shikhi.content.domain.Level;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LevelRepository extends JpaRepository<Level, UUID> {

	List<Level> findByContentVersionIdOrderByOrdinal(UUID contentVersionId);
}
