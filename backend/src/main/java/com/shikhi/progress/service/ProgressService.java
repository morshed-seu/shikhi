package com.shikhi.progress.service;

import com.shikhi.content.domain.ContentStatus;
import com.shikhi.content.repo.ContentVersionRepository;
import com.shikhi.content.repo.LessonRepository;
import com.shikhi.content.repo.LevelRepository;
import com.shikhi.content.repo.UnitRepository;
import com.shikhi.content.service.CurriculumProgressOverlay;
import com.shikhi.content.web.CurriculumTree;
import com.shikhi.content.web.CurriculumTree.LessonNode;
import com.shikhi.content.web.CurriculumTree.LevelNode;
import com.shikhi.content.web.CurriculumTree.UnitNode;
import com.shikhi.progress.domain.ProgressStatus;
import com.shikhi.progress.domain.UserProgress;
import com.shikhi.progress.domain.UserStats;
import com.shikhi.progress.repo.UserProgressRepository;
import com.shikhi.progress.repo.UserStatsRepository;
import com.shikhi.progress.web.Stats;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The progress/gamification core (LLD §2.5): persistent XP, a daily streak, hearts, lesson
 * completion, and sequential unlocking. It also implements {@link CurriculumProgressOverlay}
 * to fold a learner's progress onto the shared curriculum tree. Streak/hearts roll over on
 * the first activity of a new (UTC) day; per-user timezone is a later refinement.
 */
@Service
public class ProgressService implements CurriculumProgressOverlay {

	public static final int XP_PER_CORRECT = 10;

	private static final String COMPLETED = "COMPLETED";
	private static final String NOT_STARTED = "NOT_STARTED";

	private final UserStatsRepository stats;
	private final UserProgressRepository progress;
	private final ContentVersionRepository versions;
	private final LevelRepository levels;
	private final UnitRepository units;
	private final LessonRepository lessons;

	public ProgressService(UserStatsRepository stats, UserProgressRepository progress,
			ContentVersionRepository versions, LevelRepository levels, UnitRepository units,
			LessonRepository lessons) {
		this.stats = stats;
		this.progress = progress;
		this.versions = versions;
		this.levels = levels;
		this.units = units;
		this.lessons = lessons;
	}

	private LocalDate today() {
		return LocalDate.now(ZoneOffset.UTC);
	}

	private UserStats getOrCreate(UUID userId) {
		return stats.findById(userId).orElseGet(() -> stats.save(new UserStats(userId)));
	}

	@Transactional
	public Stats getState(UUID userId) {
		return Stats.from(getOrCreate(userId));
	}

	@Transactional(readOnly = true)
	public UUID currentPublishedVersionId() {
		return versions.findFirstByStatus(ContentStatus.PUBLISHED).map(v -> v.getId()).orElse(null);
	}

	/** Record one graded answer: mark the day active (refill/streak) and spend a heart if wrong. */
	@Transactional
	public Stats recordAnswer(UUID userId, boolean correct) {
		UserStats s = getOrCreate(userId);
		s.registerActiveDay(today());
		if (!correct) {
			s.loseHeart();
		}
		return Stats.from(stats.save(s));
	}

	/**
	 * Record one graded practice answer (E12). Unlike lessons — which award XP in a lump on
	 * completion — practice has no completion bonus, so a correct answer earns its XP right
	 * away; a wrong one costs a heart. Day activity (streak/refill) advances either way.
	 */
	@Transactional
	public Stats recordPracticeAnswer(UUID userId, boolean correct) {
		UserStats s = getOrCreate(userId);
		s.registerActiveDay(today());
		if (correct) {
			s.addXp(XP_PER_CORRECT);
		}
		else {
			s.loseHeart();
		}
		return Stats.from(stats.save(s));
	}

	/** Set the learner's CEFR band (self-placement or an accepted level-up, US-12.1/12.7). */
	@Transactional
	public Stats setLevel(UUID userId, String cefrLevel) {
		UserStats s = getOrCreate(userId);
		s.setCefrLevel(cefrLevel);
		return Stats.from(stats.save(s));
	}

	/** Finalize a lesson: award XP once, upsert progress, advance the day, unlock what follows. */
	@Transactional
	public CompletionResult completeLesson(UUID userId, UUID lessonId, UUID contentVersionId,
			int score) {
		UserStats s = getOrCreate(userId);
		s.registerActiveDay(today());

		UserProgress p = progress
				.findByUserIdAndLessonIdAndContentVersionId(userId, lessonId, contentVersionId)
				.orElseGet(() -> new UserProgress(userId, lessonId, contentVersionId));
		boolean firstCompletion = p.getStatus() != ProgressStatus.COMPLETED;
		p.complete(score);
		progress.save(p);

		int xpEarned = firstCompletion ? score * XP_PER_CORRECT : 0;
		if (xpEarned > 0) {
			s.addXp(xpEarned);
		}
		stats.save(s);

		return new CompletionResult(xpEarned, nextUnlocked(userId, contentVersionId, lessonId),
				Stats.from(s));
	}

	/** The lesson that becomes available right after {@code lessonId}, if not already done. */
	private List<String> nextUnlocked(UUID userId, UUID versionId, UUID lessonId) {
		List<UUID> order = orderedLessonIds(versionId);
		int idx = order.indexOf(lessonId);
		if (idx < 0 || idx + 1 >= order.size()) {
			return List.of();
		}
		UUID next = order.get(idx + 1);
		boolean done = progress
				.findByUserIdAndLessonIdAndContentVersionId(userId, next, versionId)
				.map(pr -> pr.getStatus() == ProgressStatus.COMPLETED)
				.orElse(false);
		return done ? List.of() : List.of(next.toString());
	}

	private List<UUID> orderedLessonIds(UUID versionId) {
		return levels.findByContentVersionIdOrderByOrdinal(versionId).stream()
				.flatMap(l -> units.findByLevelIdOrderByOrdinal(l.getId()).stream())
				.flatMap(u -> lessons.findByUnitIdOrderByOrdinal(u.getId()).stream())
				.map(com.shikhi.content.domain.Lesson::getId)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public CurriculumTree overlay(UUID userId, CurriculumTree tree) {
		if (tree.contentVersion() == null) {
			return tree;
		}
		UUID versionId = versions.findFirstByStatus(ContentStatus.PUBLISHED)
				.map(v -> v.getId()).orElse(null);
		if (versionId == null) {
			return tree;
		}

		Set<UUID> completed = progress.findByUserIdAndContentVersionId(userId, versionId).stream()
				.filter(p -> p.getStatus() == ProgressStatus.COMPLETED)
				.map(UserProgress::getLessonId)
				.collect(Collectors.toSet());

		// Sequential unlock: the first lesson, plus any lesson whose predecessor is completed.
		List<UUID> order = tree.levels().stream()
				.flatMap(l -> l.units().stream())
				.flatMap(u -> u.lessons().stream())
				.map(LessonNode::id)
				.toList();
		Set<UUID> unlocked = new HashSet<>(completed);
		for (int i = 0; i < order.size(); i++) {
			if (i == 0 || completed.contains(order.get(i - 1))) {
				unlocked.add(order.get(i));
			}
		}

		List<LevelNode> overlaidLevels = tree.levels().stream()
				.map(level -> overlayLevel(level, completed, unlocked))
				.toList();
		return new CurriculumTree(tree.contentVersion(), overlaidLevels);
	}

	private LevelNode overlayLevel(LevelNode level, Set<UUID> completed, Set<UUID> unlocked) {
		List<UnitNode> overlaidUnits = level.units().stream()
				.map(unit -> overlayUnit(unit, completed, unlocked))
				.toList();
		return new LevelNode(level.id(), level.code(), level.title(), level.ordinal(),
				overlaidUnits);
	}

	private UnitNode overlayUnit(UnitNode unit, Set<UUID> completed, Set<UUID> unlocked) {
		List<LessonNode> overlaidLessons = unit.lessons().stream()
				.map(lesson -> new LessonNode(lesson.id(), lesson.title(), lesson.ordinal(),
						completed.contains(lesson.id()) ? COMPLETED : NOT_STARTED,
						!unlocked.contains(lesson.id())))
				.toList();
		boolean unitLocked = overlaidLessons.stream().allMatch(LessonNode::locked);
		return new UnitNode(unit.id(), unit.code(), unit.title(), unit.ordinal(), unitLocked,
				overlaidLessons);
	}
}
