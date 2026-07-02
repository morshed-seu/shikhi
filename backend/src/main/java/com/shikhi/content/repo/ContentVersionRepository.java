package com.shikhi.content.repo;

import com.shikhi.content.domain.ContentStatus;
import com.shikhi.content.domain.ContentVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentVersionRepository extends JpaRepository<ContentVersion, UUID> {

	Optional<ContentVersion> findFirstByStatus(ContentStatus status);

	boolean existsByLabel(String label);
}
