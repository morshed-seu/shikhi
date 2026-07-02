package com.shikhi.identity.repo;

import com.shikhi.identity.domain.Credential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

	Optional<Credential> findByUserId(UUID userId);
}
