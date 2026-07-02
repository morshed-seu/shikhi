package com.shikhi.identity.service;

import com.shikhi.identity.repo.RefreshTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a refresh-token family in its own committed transaction. Kept separate so the
 * replay-response revocation persists even though the surrounding request transaction is
 * about to roll back (the rotation call throws 401 on detecting replay).
 */
@Component
class RefreshTokenFamilyRevoker {

	private final RefreshTokenRepository repository;

	RefreshTokenFamilyRevoker(RefreshTokenRepository repository) {
		this.repository = repository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void revokeFamily(UUID familyId) {
		repository.revokeFamily(familyId, Instant.now());
	}
}
