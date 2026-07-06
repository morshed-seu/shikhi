package com.shikhi.identity.repo;

import com.shikhi.identity.domain.User;
import com.shikhi.identity.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

	/**
	 * Hard-delete abandoned guests: anonymous accounts not touched since {@code cutoff}. There's
	 * no PII worth keeping for a never-converted guest, and the DB {@code on delete cascade} FKs
	 * reclaim all their progress/tokens. Returns the number of rows removed. See GuestReaper.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from User u where u.status = :status and u.updatedAt < :cutoff")
	int deleteStaleByStatus(@Param("status") UserStatus status, @Param("cutoff") Instant cutoff);
}
