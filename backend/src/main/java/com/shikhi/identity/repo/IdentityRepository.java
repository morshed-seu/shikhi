package com.shikhi.identity.repo;

import com.shikhi.identity.domain.Identity;
import com.shikhi.identity.domain.Provider;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityRepository extends JpaRepository<Identity, UUID> {

	Optional<Identity> findByProviderAndExternalRef(Provider provider, String externalRef);

	boolean existsByProviderAndExternalRef(Provider provider, String externalRef);

	List<Identity> findByUserId(UUID userId);
}
