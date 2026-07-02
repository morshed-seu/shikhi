package com.shikhi.content.service;

import com.shikhi.content.CacheConfig;
import com.shikhi.content.domain.ContentStatus;
import com.shikhi.content.domain.ContentVersion;
import com.shikhi.content.domain.Exercise;
import com.shikhi.content.domain.ExerciseAnswer;
import com.shikhi.content.domain.ExerciseOption;
import com.shikhi.content.domain.ExerciseType;
import com.shikhi.content.domain.Lesson;
import com.shikhi.content.domain.Level;
import com.shikhi.content.domain.Unit;
import com.shikhi.content.repo.ContentVersionRepository;
import com.shikhi.content.repo.ExerciseAnswerRepository;
import com.shikhi.content.repo.ExerciseOptionRepository;
import com.shikhi.content.repo.ExerciseRepository;
import com.shikhi.content.repo.LessonRepository;
import com.shikhi.content.repo.LevelRepository;
import com.shikhi.content.repo.UnitRepository;
import com.shikhi.content.web.ContentVersionResponse;
import com.shikhi.content.web.ValidationResult;
import com.shikhi.content.web.ValidationResult.Issue;
import com.shikhi.platform.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write side of the content module: branch a draft from the published version, validate it,
 * and publish it (versioned + immutable-once-published). Content rows themselves are authored
 * via the content pipeline/seed; this service owns the version lifecycle (LLD §2.2).
 */
@Service
public class AuthoringService {

	private final ContentVersionRepository versions;
	private final LevelRepository levels;
	private final UnitRepository units;
	private final LessonRepository lessons;
	private final ExerciseRepository exercises;
	private final ExerciseOptionRepository options;
	private final ExerciseAnswerRepository answers;

	public AuthoringService(ContentVersionRepository versions, LevelRepository levels,
			UnitRepository units, LessonRepository lessons, ExerciseRepository exercises,
			ExerciseOptionRepository options, ExerciseAnswerRepository answers) {
		this.versions = versions;
		this.levels = levels;
		this.units = units;
		this.lessons = lessons;
		this.exercises = exercises;
		this.options = options;
		this.answers = answers;
	}

	/** Create a new DRAFT by deep-copying the current published tree (new ids throughout). */
	@Transactional
	public ContentVersionResponse createDraft() {
		ContentVersion base = versions.findFirstByStatus(ContentStatus.PUBLISHED)
				.orElseThrow(() -> ApiException.conflict("NO_BASE_CONTENT",
						"No published content to branch a draft from"));

		ContentVersion draft = new ContentVersion("draft-" + UUID.randomUUID().toString().substring(0, 8),
				"Branched from " + base.getLabel());
		versions.save(draft);
		cloneTree(base.getId(), draft.getId());
		return ContentVersionResponse.from(draft);
	}

	@Transactional(readOnly = true)
	public ValidationResult validate(UUID versionId) {
		versions.findById(versionId)
				.orElseThrow(() -> ApiException.notFound("Content version not found"));
		List<Issue> issues = collectIssues(versionId);
		return new ValidationResult(issues.isEmpty(), issues);
	}

	@Transactional
	@CacheEvict(cacheNames = { CacheConfig.CURRICULUM_TREE, CacheConfig.LESSONS },
			allEntries = true)
	public ContentVersionResponse publish(UUID versionId) {
		ContentVersion draft = versions.findById(versionId)
				.orElseThrow(() -> ApiException.notFound("Content version not found"));
		if (draft.getStatus() != ContentStatus.DRAFT) {
			throw ApiException.conflict("NOT_DRAFT", "Only draft versions can be published");
		}
		List<Issue> issues = collectIssues(versionId);
		if (!issues.isEmpty()) {
			throw ApiException.conflict("VALIDATION_FAILED",
					"Draft has " + issues.size() + " validation issue(s); validate before publishing");
		}

		versions.findFirstByStatus(ContentStatus.PUBLISHED).ifPresent(prev -> {
			prev.archive();
			versions.save(prev);
		});
		draft.publish();
		versions.save(draft);
		return ContentVersionResponse.from(draft);
	}

	// --- validation ---

	private List<Issue> collectIssues(UUID versionId) {
		List<Issue> issues = new ArrayList<>();
		List<Level> versionLevels = levels.findByContentVersionIdOrderByOrdinal(versionId);
		if (versionLevels.isEmpty()) {
			issues.add(new Issue("levels", "EMPTY", "A version must have at least one level"));
		}
		for (Level level : versionLevels) {
			List<Unit> levelUnits = units.findByLevelIdOrderByOrdinal(level.getId());
			if (levelUnits.isEmpty()) {
				issues.add(new Issue("level:" + level.getCode(), "EMPTY",
						"Level has no units"));
			}
			for (Unit unit : levelUnits) {
				validateUnit(unit, issues);
			}
		}
		return issues;
	}

	private void validateUnit(Unit unit, List<Issue> issues) {
		List<Lesson> unitLessons = lessons.findByUnitIdOrderByOrdinal(unit.getId());
		if (unitLessons.isEmpty()) {
			issues.add(new Issue("unit:" + unit.getCode(), "EMPTY", "Unit has no lessons"));
		}
		for (Lesson lesson : unitLessons) {
			List<Exercise> lessonExercises = exercises.findByLessonIdOrderByOrdinal(lesson.getId());
			if (lessonExercises.isEmpty()) {
				issues.add(new Issue("lesson:" + lesson.getCode(), "EMPTY",
						"Lesson has no exercises"));
			}
			for (Exercise exercise : lessonExercises) {
				validateExercise(lesson, exercise, issues);
			}
		}
	}

	private void validateExercise(Lesson lesson, Exercise exercise, List<Issue> issues) {
		String path = "lesson:" + lesson.getCode() + "/ex:" + exercise.getOrdinal();
		if (exercise.getType() == ExerciseType.MCQ) {
			List<ExerciseOption> opts = options.findByExerciseIdOrderByOrdinal(exercise.getId());
			long correct = opts.stream().filter(ExerciseOption::isCorrect).count();
			if (opts.size() < 2) {
				issues.add(new Issue(path, "TOO_FEW_OPTIONS", "MCQ needs at least two options"));
			}
			if (correct != 1) {
				issues.add(new Issue(path, "BAD_CORRECT_COUNT",
						"MCQ must have exactly one correct option"));
			}
		} else if (exercise.getType() == ExerciseType.TYPE_TRANSLATION
				|| exercise.getType() == ExerciseType.FILL_BLANK) {
			if (answers.findByExerciseId(exercise.getId()).isEmpty()) {
				issues.add(new Issue(path, "NO_ACCEPTED_ANSWER",
						"Text answer exercise needs at least one accepted answer"));
			}
		}
	}

	// --- cloning ---

	private void cloneTree(UUID fromVersionId, UUID toVersionId) {
		for (Level level : levels.findByContentVersionIdOrderByOrdinal(fromVersionId)) {
			Level newLevel = levels.save(new Level(toVersionId, level.getCode(),
					level.getTitleEn(), level.getTitleBn(), level.getOrdinal()));
			for (Unit unit : units.findByLevelIdOrderByOrdinal(level.getId())) {
				Unit newUnit = units.save(new Unit(newLevel.getId(), unit.getCode(),
						unit.getTitleEn(), unit.getTitleBn(), unit.getOrdinal()));
				cloneLessons(unit.getId(), newUnit.getId());
			}
		}
	}

	private void cloneLessons(UUID fromUnitId, UUID toUnitId) {
		for (Lesson lesson : lessons.findByUnitIdOrderByOrdinal(fromUnitId)) {
			Lesson newLesson = lessons.save(new Lesson(toUnitId, lesson.getCode(),
					lesson.getTitleEn(), lesson.getTitleBn(), lesson.getOrdinal()));
			for (Exercise exercise : exercises.findByLessonIdOrderByOrdinal(lesson.getId())) {
				Exercise newExercise = exercises.save(new Exercise(newLesson.getId(),
						exercise.getType(), exercise.getOrdinal(), exercise.getPromptEn(),
						exercise.getPromptBn(), exercise.getMediaRef()));
				cloneChildren(exercise.getId(), newExercise.getId());
			}
		}
	}

	private void cloneChildren(UUID fromExerciseId, UUID toExerciseId) {
		for (ExerciseOption option : options.findByExerciseIdOrderByOrdinal(fromExerciseId)) {
			options.save(new ExerciseOption(toExerciseId, option.getTextEn(), option.getTextBn(),
					option.isCorrect(), option.getOrdinal()));
		}
		for (ExerciseAnswer answer : answers.findByExerciseId(fromExerciseId)) {
			answers.save(new ExerciseAnswer(toExerciseId, answer.getAcceptedAnswer(),
					answer.isPrimary()));
		}
	}
}
