package com.shikhi.progress.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikhi.practice.domain.PracticeWordProgress;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.practice.schedule.ReviewProgress;
import com.shikhi.practice.schedule.ReviewProgressRepository;
import com.shikhi.progress.domain.UserProgress;
import com.shikhi.progress.repo.UserProgressRepository;
import com.shikhi.progress.web.ProgressSnapshotResponse;
import com.shikhi.progress.web.Stats;
import com.shikhi.support.InMemoryJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * {@link ProgressSnapshotService} composes {@link ProgressService#getState} with three
 * read-only repository scans (UO5). No Mockito on the test classpath (see
 * {@code ProgressEventApplierTest}): {@link ProgressService} is faked by subclassing (same
 * technique as {@code ProgressEventApplierTest}), and the three repositories are hand-rolled
 * in-memory fakes that — unlike the throw-away stubs in {@code ProgressEventApplierTest}/
 * {@code WordProgressServiceTest}, which never exercise the UO5 query methods — actually
 * implement {@code findByKey_UserId*}/{@code findByUserId*} so this test can assert real
 * filtering behavior (user scoping, the {@code since} boundary, completed-only lessons)
 * end-to-end through the service without standing up Spring or a database.
 */
class ProgressSnapshotServiceTest {

	private final UUID userId = UUID.randomUUID();
	private final UUID otherUserId = UUID.randomUUID();

	private final FakeProgressService progressService = new FakeProgressService();
	private final InMemoryWordProgressRepository wordProgress =
			new InMemoryWordProgressRepository();
	private final InMemoryReviewProgressRepository reviewProgress =
			new InMemoryReviewProgressRepository();
	private final InMemoryUserProgressRepository userProgress =
			new InMemoryUserProgressRepository();

	private final ProgressSnapshotService service = new ProgressSnapshotService(progressService,
			wordProgress, reviewProgress, userProgress);

	@Test
	void snapshotForAUserWithNoActivityIsAllEmptyWithDefaultStats() {
		ProgressSnapshotResponse response = service.snapshot(userId, null);

		assertThat(response.stats().xp()).isZero();
		assertThat(response.stats().hearts()).isEqualTo(5);
		assertThat(response.stats().cefrLevel()).isEqualTo("A1");
		assertThat(response.wordProgress()).isEmpty();
		assertThat(response.reviewProgress()).isEmpty();
		assertThat(response.completedLessons()).isEmpty();
		assertThat(response.serverTime()).isNotNull();
	}

	@Test
	void snapshotOnlyReturnsTheAuthenticatedUsersOwnRowsNotAnotherUsers() {
		UUID vocabularyId = UUID.randomUUID();
		Instant seenAt = Instant.parse("2026-07-10T00:00:00Z");

		PracticeWordProgress mine = new PracticeWordProgress(userId, vocabularyId);
		mine.recordAnswer(true, seenAt);
		wordProgress.save(mine);

		PracticeWordProgress someoneElses = new PracticeWordProgress(otherUserId, vocabularyId);
		someoneElses.recordAnswer(true, seenAt);
		wordProgress.save(someoneElses);

		ProgressSnapshotResponse response = service.snapshot(userId, null);

		assertThat(response.wordProgress()).hasSize(1);
		assertThat(response.wordProgress().get(0).vocabularyId()).isEqualTo(vocabularyId);
	}

	@Test
	void sinceCursorIsStrictlyExclusiveOnTheBoundary() {
		UUID vocabAtCursor = UUID.randomUUID();
		UUID vocabAfterCursor = UUID.randomUUID();
		Instant cursor = Instant.parse("2026-07-15T00:00:00Z");

		PracticeWordProgress atCursor = new PracticeWordProgress(userId, vocabAtCursor);
		atCursor.recordAnswer(true, cursor);
		wordProgress.save(atCursor);

		PracticeWordProgress afterCursor = new PracticeWordProgress(userId, vocabAfterCursor);
		afterCursor.recordAnswer(true, cursor.plusSeconds(1));
		wordProgress.save(afterCursor);

		ProgressSnapshotResponse response = service.snapshot(userId, cursor);

		assertThat(response.wordProgress()).hasSize(1);
		assertThat(response.wordProgress().get(0).vocabularyId()).isEqualTo(vocabAfterCursor);
	}

	@Test
	void onlyCompletedLessonsAreReturnedNotAnUnfinishedRow() {
		UUID lessonId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		UserProgress completed = new UserProgress(userId, lessonId, versionId);
		completed.complete(3);
		userProgress.save(completed);

		UUID notStartedLessonId = UUID.randomUUID();
		userProgress.save(new UserProgress(userId, notStartedLessonId, versionId));

		ProgressSnapshotResponse response = service.snapshot(userId, null);

		assertThat(response.completedLessons()).hasSize(1);
		assertThat(response.completedLessons().get(0).lessonId()).isEqualTo(lessonId);
		assertThat(response.completedLessons().get(0).contentVersionId()).isEqualTo(versionId);
		assertThat(response.completedLessons().get(0).score()).isEqualTo(3);
	}

	// ---- fakes ------------------------------------------------------------------------------

	/** Subclass-based spy, same technique as {@code ProgressEventApplierTest.FakeProgressService}:
	 * {@link ProgressService} has no interface and a constructor pulling in repositories/a
	 * transaction manager this unit test has no reason to stand up. */
	private static class FakeProgressService extends ProgressService {

		FakeProgressService() {
			super(null, null, null, null, null, null, null);
		}

		@Override
		public Stats getState(UUID userId) {
			return new Stats(0, 0, 0, 0, 5, 20, "A1", Map.of());
		}
	}

	private static class InMemoryWordProgressRepository
			extends InMemoryJpaRepository<PracticeWordProgress, PracticeWordProgress.Key>
			implements PracticeWordProgressRepository {

		@Override
		protected PracticeWordProgress.Key idOf(PracticeWordProgress entity) {
			return entity.getKey();
		}

		@Override
		public long countMasteredInBand(UUID userId, String cefrLevel) {
			throw new UnsupportedOperationException("not needed by this test");
		}

		@Override
		public List<PracticeWordProgress> findByKey_UserId(UUID userId) {
			return findAll().stream().filter(p -> p.getKey().userId().equals(userId)).toList();
		}

		@Override
		public List<PracticeWordProgress> findByKey_UserIdAndLastSeenAtAfter(UUID userId,
				Instant since) {
			return findByKey_UserId(userId).stream()
					.filter(p -> p.getLastSeenAt().isAfter(since)).toList();
		}
	}

	private static class InMemoryReviewProgressRepository
			extends InMemoryJpaRepository<ReviewProgress, ReviewProgress.Key>
			implements ReviewProgressRepository {

		@Override
		protected ReviewProgress.Key idOf(ReviewProgress entity) {
			return entity.getKey();
		}

		@Override
		public List<ReviewProgress> findByKey_UserId(UUID userId) {
			return findAll().stream().filter(r -> r.getKey().userId().equals(userId)).toList();
		}

		@Override
		public List<ReviewProgress> findByKey_UserIdAndUpdatedAtAfter(UUID userId, Instant since) {
			return findByKey_UserId(userId).stream()
					.filter(r -> r.getUpdatedAt().isAfter(since)).toList();
		}
	}

	/** {@link UserProgress} has no exposed id getter (only JPA needs it); {@code idOf} mints a
	 * fresh key per save, same idiom as {@code ProgressEventApplierTest.FakeProcessedEventRepository}
	 * — fine since this fake is never asked to update an already-saved row in place. */
	private static class InMemoryUserProgressRepository
			extends InMemoryJpaRepository<UserProgress, UUID>
			implements UserProgressRepository {

		@Override
		protected UUID idOf(UserProgress entity) {
			return UUID.randomUUID();
		}

		@Override
		public List<UserProgress> findByUserIdAndContentVersionId(UUID userId,
				UUID contentVersionId) {
			throw new UnsupportedOperationException("not needed by this test");
		}

		@Override
		public Optional<UserProgress> findByUserIdAndLessonIdAndContentVersionId(UUID userId,
				UUID lessonId, UUID contentVersionId) {
			throw new UnsupportedOperationException("not needed by this test");
		}

		@Override
		public List<UserProgress> findByUserId(UUID userId) {
			return findAll().stream().filter(p -> p.getUserId().equals(userId)).toList();
		}

		@Override
		public List<UserProgress> findByUserIdAndCompletedAtAfter(UUID userId, Instant since) {
			return findByUserId(userId).stream()
					.filter(p -> p.getCompletedAt() != null && p.getCompletedAt().isAfter(since))
					.toList();
		}
	}
}
