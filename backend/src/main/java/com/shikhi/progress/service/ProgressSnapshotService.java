package com.shikhi.progress.service;

import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.practice.schedule.ReviewProgressRepository;
import com.shikhi.progress.domain.ProgressStatus;
import com.shikhi.progress.repo.UserProgressRepository;
import com.shikhi.progress.web.ProgressSnapshotResponse;
import com.shikhi.progress.web.ProgressSnapshotResponse.CompletedLessonEntry;
import com.shikhi.progress.web.ProgressSnapshotResponse.ReviewProgressEntry;
import com.shikhi.progress.web.ProgressSnapshotResponse.WordProgressEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only, authoritative per-user snapshot (UO5) — rebuilds local Android progress from
 * server truth on a reinstall or a second device. Every list is naturally scoped to rows the
 * server already only persists for seen/graduated words or completed lessons, so no further
 * filtering is needed beyond the authenticated user and the optional {@code since} cursor. This
 * endpoint never writes: it composes {@link ProgressService#getState} with three read-only
 * repository scans, mirroring how {@link ProgressSyncService} composes the same
 * repositories/service for its write path.
 */
@Service
public class ProgressSnapshotService {

	private final ProgressService progress;
	private final PracticeWordProgressRepository wordProgress;
	private final ReviewProgressRepository reviewProgress;
	private final UserProgressRepository userProgress;

	public ProgressSnapshotService(ProgressService progress,
			PracticeWordProgressRepository wordProgress, ReviewProgressRepository reviewProgress,
			UserProgressRepository userProgress) {
		this.progress = progress;
		this.wordProgress = wordProgress;
		this.reviewProgress = reviewProgress;
		this.userProgress = userProgress;
	}

	/**
	 * @param since when non-null, each list is narrowed to rows touched strictly after this
	 *              instant (designed in now for a future incremental pull); {@code null} returns
	 *              everything the server has for this user.
	 */
	@Transactional(readOnly = true)
	public ProgressSnapshotResponse snapshot(UUID userId, Instant since) {
		List<WordProgressEntry> words = (since == null
				? wordProgress.findByKey_UserId(userId)
				: wordProgress.findByKey_UserIdAndLastSeenAtAfter(userId, since))
				.stream().map(WordProgressEntry::from).toList();

		List<ReviewProgressEntry> reviews = (since == null
				? reviewProgress.findByKey_UserId(userId)
				: reviewProgress.findByKey_UserIdAndUpdatedAtAfter(userId, since))
				.stream().map(ReviewProgressEntry::from).toList();

		List<CompletedLessonEntry> lessons = (since == null
				? userProgress.findByUserId(userId)
				: userProgress.findByUserIdAndCompletedAtAfter(userId, since))
				.stream()
				.filter(p -> p.getStatus() == ProgressStatus.COMPLETED)
				.map(CompletedLessonEntry::from).toList();

		return new ProgressSnapshotResponse(progress.getState(userId), words, reviews, lessons,
				Instant.now());
	}
}
