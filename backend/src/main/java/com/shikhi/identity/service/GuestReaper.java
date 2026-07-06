package com.shikhi.identity.service;

import com.shikhi.identity.domain.UserStatus;
import com.shikhi.identity.repo.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reclaims abandoned guest accounts so anonymous rows don't accumulate. A guest that never
 * converts (added no email/password) is hard-deleted once it's been idle longer than
 * {@code shikhi.identity.guest-ttl}; the DB {@code on delete cascade} FKs remove its progress.
 * Runs daily. See ADR-0011 and docs/50-security-and-privacy.md (guest data retention).
 */
@Component
public class GuestReaper {

	private static final Logger log = LoggerFactory.getLogger(GuestReaper.class);

	private final UserRepository users;
	private final Duration guestTtl;

	public GuestReaper(UserRepository users,
			@Value("${shikhi.identity.guest-ttl:P30D}") Duration guestTtl) {
		this.users = users;
		this.guestTtl = guestTtl;
	}

	@Scheduled(cron = "${shikhi.identity.guest-reaper-cron:0 0 3 * * *}")
	@Transactional
	public void reap() {
		Instant cutoff = Instant.now().minus(guestTtl);
		int removed = users.deleteStaleByStatus(UserStatus.ANONYMOUS, cutoff);
		if (removed > 0) {
			log.info("Reaped {} abandoned guest account(s) idle since before {}", removed, cutoff);
		}
	}
}
