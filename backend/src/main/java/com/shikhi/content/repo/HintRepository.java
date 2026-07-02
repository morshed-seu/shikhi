package com.shikhi.content.repo;

import com.shikhi.content.domain.Hint;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HintRepository extends JpaRepository<Hint, UUID> {

	List<Hint> findByExerciseId(UUID exerciseId);
}
