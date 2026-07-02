package com.shikhi.content.service;

import com.shikhi.content.CacheConfig;
import com.shikhi.content.domain.ContentStatus;
import com.shikhi.content.domain.ContentVersion;
import com.shikhi.content.domain.Exercise;
import com.shikhi.content.domain.ExerciseType;
import com.shikhi.content.domain.Lesson;
import com.shikhi.content.domain.Level;
import com.shikhi.content.domain.Unit;
import com.shikhi.content.repo.ContentVersionRepository;
import com.shikhi.content.repo.ExerciseOptionRepository;
import com.shikhi.content.repo.ExerciseRepository;
import com.shikhi.content.repo.LessonRepository;
import com.shikhi.content.repo.LevelRepository;
import com.shikhi.content.repo.UnitRepository;
import com.shikhi.content.web.Bilingual;
import com.shikhi.content.web.CurriculumTree;
import com.shikhi.content.web.LessonView;
import com.shikhi.platform.error.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of the content module: serves the published curriculum tree and lessons. */
@Service
public class CurriculumQueryService {

	private static final String NOT_STARTED = "NOT_STARTED";

	private final ContentVersionRepository versions;
	private final LevelRepository levels;
	private final UnitRepository units;
	private final LessonRepository lessons;
	private final ExerciseRepository exercises;
	private final ExerciseOptionRepository options;

	public CurriculumQueryService(ContentVersionRepository versions, LevelRepository levels,
			UnitRepository units, LessonRepository lessons, ExerciseRepository exercises,
			ExerciseOptionRepository options) {
		this.versions = versions;
		this.levels = levels;
		this.units = units;
		this.lessons = lessons;
		this.exercises = exercises;
		this.options = options;
	}

	@Cacheable(CacheConfig.CURRICULUM_TREE)
	@Transactional(readOnly = true)
	public CurriculumTree getPublishedTree() {
		ContentVersion published = versions.findFirstByStatus(ContentStatus.PUBLISHED).orElse(null);
		if (published == null) {
			return new CurriculumTree(null, List.of());
		}

		List<CurriculumTree.LevelNode> levelNodes = levels
				.findByContentVersionIdOrderByOrdinal(published.getId()).stream()
				.map(this::toLevelNode)
				.toList();
		return new CurriculumTree(published.getLabel(), levelNodes);
	}

	@Cacheable(cacheNames = CacheConfig.LESSONS, key = "#lessonId")
	@Transactional(readOnly = true)
	public LessonView getLesson(UUID lessonId) {
		ContentVersion published = versions.findFirstByStatus(ContentStatus.PUBLISHED)
				.orElseThrow(() -> ApiException.notFound("Lesson not found"));
		Lesson lesson = lessons.findById(lessonId)
				.orElseThrow(() -> ApiException.notFound("Lesson not found"));

		// Only serve lessons that belong to the currently published version.
		Unit unit = units.findById(lesson.getUnitId())
				.orElseThrow(() -> ApiException.notFound("Lesson not found"));
		Level level = levels.findById(unit.getLevelId())
				.orElseThrow(() -> ApiException.notFound("Lesson not found"));
		if (!level.getContentVersionId().equals(published.getId())) {
			throw ApiException.notFound("Lesson not found");
		}

		List<LessonView.ExerciseView> exerciseViews = exercises
				.findByLessonIdOrderByOrdinal(lessonId).stream()
				.map(this::toExerciseView)
				.toList();
		return new LessonView(lesson.getId(), published.getLabel(),
				new Bilingual(lesson.getTitleEn(), lesson.getTitleBn()), exerciseViews);
	}

	private CurriculumTree.LevelNode toLevelNode(Level level) {
		List<CurriculumTree.UnitNode> unitNodes = units
				.findByLevelIdOrderByOrdinal(level.getId()).stream()
				.map(this::toUnitNode)
				.toList();
		return new CurriculumTree.LevelNode(level.getId(), level.getCode(),
				new Bilingual(level.getTitleEn(), level.getTitleBn()), level.getOrdinal(),
				unitNodes);
	}

	private CurriculumTree.UnitNode toUnitNode(Unit unit) {
		List<CurriculumTree.LessonNode> lessonNodes = lessons
				.findByUnitIdOrderByOrdinal(unit.getId()).stream()
				.map(lesson -> new CurriculumTree.LessonNode(lesson.getId(),
						new Bilingual(lesson.getTitleEn(), lesson.getTitleBn()),
						lesson.getOrdinal(), NOT_STARTED, false))
				.toList();
		// locked=false for M2; unit/lesson unlocking is added with progress in M4.
		return new CurriculumTree.UnitNode(unit.getId(), unit.getCode(),
				new Bilingual(unit.getTitleEn(), unit.getTitleBn()), unit.getOrdinal(), false,
				lessonNodes);
	}

	private LessonView.ExerciseView toExerciseView(Exercise exercise) {
		Map<String, Object> config = new LinkedHashMap<>();
		if (exercise.getType() == ExerciseType.MCQ || exercise.getType() == ExerciseType.MATCH) {
			// Option text only — correctness flags stay server-side (grading is server-side).
			List<Map<String, Object>> optionViews = options
					.findByExerciseIdOrderByOrdinal(exercise.getId()).stream()
					.map(o -> Map.<String, Object>of("id", o.getId().toString(), "text",
							new Bilingual(o.getTextEn(), o.getTextBn())))
					.toList();
			config.put("options", optionViews);
		}
		return new LessonView.ExerciseView(exercise.getId(), exercise.getType().name(),
				exercise.getOrdinal(),
				new Bilingual(exercise.getPromptEn(), exercise.getPromptBn()),
				exercise.getMediaRef(), List.of(), config);
	}
}
