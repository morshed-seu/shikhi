package com.shikhi.identity.repo;

import com.shikhi.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	List<RefreshToken> findByUserId(UUID userId);

	/** Revoke every still-active token in a rotation family (replay response). */
	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now "
			+ "where t.familyId = :familyId and t.revokedAt is null")
	int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

	/** Revoke every still-active token for a user (logout everywhere). */
	@Modifying
	@Query("update RefreshToken t set t.revokedAt = :now "
			+ "where t.userId = :userId and t.revokedAt is null")
	int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
