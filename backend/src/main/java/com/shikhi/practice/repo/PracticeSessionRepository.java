package com.shikhi.practice.repo;

import com.shikhi.practice.domain.PracticeSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, UUID> {
}
