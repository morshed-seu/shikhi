package com.shikhi.content.tool;

import com.shikhi.ShikhiApplication;
import com.shikhi.content.domain.ContentStatus;
import com.shikhi.content.domain.ContentVersion;
import com.shikhi.content.domain.Exercise;
import com.shikhi.content.domain.ExerciseAnswer;
import com.shikhi.content.domain.ExerciseOption;
import com.shikhi.content.domain.Hint;
import com.shikhi.content.domain.Lesson;
import com.shikhi.content.domain.Level;
import com.shikhi.content.domain.Unit;
import com.shikhi.content.domain.Vocabulary;
import com.shikhi.content.repo.ExerciseAnswerRepository;
import com.shikhi.content.repo.ExerciseOptionRepository;
import com.shikhi.content.repo.ExerciseRepository;
import com.shikhi.content.repo.HintRepository;
import com.shikhi.content.repo.LessonRepository;
import com.shikhi.content.repo.LevelRepository;
import com.shikhi.content.repo.UnitRepository;
import com.shikhi.content.repo.VocabularyRepository;
import com.shikhi.content.repo.ContentVersionRepository;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * Dev-only, build-time export tool. Dumps the published content graph (Oxford-5000 vocabulary +
 * the pilot curriculum tree, including answer-key tables: {@code exercise_options.is_correct},
 * {@code exercise_answers.accepted_answer}) to the two JSON files bundled into the Android app
 * as {@code assets/content-seed/} (see {@code docs/93-offline-learning-design.md} §6, gate OF1).
 *
 * <p><b>Not part of normal app startup.</b> {@link ShikhiApplication#main} is the real production
 * entry point; this class has its own {@code main()} and is only ever invoked directly, e.g. via
 * the {@code contentExport} Gradle task added for this purpose:
 *
 * <pre>{@code
 * ./gradlew contentExport
 * ./gradlew contentExport --args="/absolute/output/dir"
 * }</pre>
 *
 * <p>Boots the full app context (profile {@code dev}) with the web server disabled so it reuses
 * the existing JPA repositories/datasource config unchanged, runs Flyway migrations as a normal
 * side effect of context startup (nothing export-specific there), queries everything read-only,
 * writes {@code vocabulary.json} and {@code curriculum.json}, then exits. Requires a reachable,
 * migrated Postgres (see repo root {@code docker-compose.yml}).
 */
public final class ContentExportTool {

	private static final List<String> CEFR_LEVELS = List.of("A1", "A2", "B1", "B2", "C1");

	private static final String DEFAULT_OUTPUT_DIR =
			"../android/app/src/main/assets/content-seed";

	private ContentExportTool() {
		// CLI entry point only
	}

	public static void main(String[] args) throws IOException {
		String outputDir = args.length > 0 ? args[0] : DEFAULT_OUTPUT_DIR;
		Path outDir = Path.of(outputDir).toAbsolutePath().normalize();
		Files.createDirectories(outDir);

		ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ShikhiApplication.class)
				.web(WebApplicationType.NONE)
				.profiles("dev")
				.run();

		try {
			ObjectMapper mapper = new ObjectMapper();

			List<VocabularyRow> vocabulary = exportVocabulary(ctx);
			CurriculumExport curriculum = exportCurriculum(ctx);

			File vocabFile = outDir.resolve("vocabulary.json").toFile();
			File curriculumFile = outDir.resolve("curriculum.json").toFile();
			mapper.writeValue(vocabFile, vocabulary);
			mapper.writeValue(curriculumFile, curriculum);

			System.out.println("Wrote " + vocabulary.size() + " vocabulary rows to " + vocabFile);
			System.out.println("Wrote curriculum tree to " + curriculumFile + " ("
					+ curriculum.levels().size() + " levels, " + curriculum.units().size()
					+ " units, " + curriculum.lessons().size() + " lessons, "
					+ curriculum.exercises().size() + " exercises, "
					+ curriculum.exerciseOptions().size() + " options, "
					+ curriculum.exerciseAnswers().size() + " answers, "
					+ curriculum.hints().size() + " hints)");
		} finally {
			ctx.close();
		}
	}

	private static List<VocabularyRow> exportVocabulary(ConfigurableApplicationContext ctx) {
		VocabularyRepository vocabRepo = ctx.getBean(VocabularyRepository.class);
		List<VocabularyRow> rows = new ArrayList<>();
		for (String cefrLevel : CEFR_LEVELS) {
			for (Vocabulary v : vocabRepo.findByCefrLevelOrderByOrdinal(cefrLevel)) {
				rows.add(new VocabularyRow(v.getId().toString(), v.getHeadword(), v.getSenseLabel(),
						v.getPartOfSpeech(), v.getCefrLevel(), v.getBnGloss(), v.getExampleEn(),
						v.getExampleBn(), v.getOrdinal()));
			}
		}
		return rows;
	}

	private static CurriculumExport exportCurriculum(ConfigurableApplicationContext ctx) {
		ContentVersionRepository versionRepo = ctx.getBean(ContentVersionRepository.class);
		LevelRepository levelRepo = ctx.getBean(LevelRepository.class);
		UnitRepository unitRepo = ctx.getBean(UnitRepository.class);
		LessonRepository lessonRepo = ctx.getBean(LessonRepository.class);
		ExerciseRepository exerciseRepo = ctx.getBean(ExerciseRepository.class);
		ExerciseOptionRepository optionRepo = ctx.getBean(ExerciseOptionRepository.class);
		ExerciseAnswerRepository answerRepo = ctx.getBean(ExerciseAnswerRepository.class);
		HintRepository hintRepo = ctx.getBean(HintRepository.class);

		ContentVersion published = versionRepo.findFirstByStatus(ContentStatus.PUBLISHED)
				.orElseThrow(() -> new IllegalStateException(
						"No PUBLISHED content version found — run migrations against a fresh DB "
								+ "first (see docs/93-offline-learning-design.md §6)."));

		List<LevelRow> levelRows = new ArrayList<>();
		List<UnitRow> unitRows = new ArrayList<>();
		List<LessonRow> lessonRows = new ArrayList<>();
		List<ExerciseRow> exerciseRows = new ArrayList<>();
		List<ExerciseOptionRow> optionRows = new ArrayList<>();
		List<ExerciseAnswerRow> answerRows = new ArrayList<>();
		List<HintRow> hintRows = new ArrayList<>();

		for (Level level : levelRepo.findByContentVersionIdOrderByOrdinal(published.getId())) {
			levelRows.add(new LevelRow(level.getId().toString(), level.getCode(),
					level.getTitleEn(), level.getTitleBn(), level.getOrdinal()));

			for (Unit unit : unitRepo.findByLevelIdOrderByOrdinal(level.getId())) {
				unitRows.add(new UnitRow(unit.getId().toString(), level.getId().toString(),
						unit.getCode(), unit.getTitleEn(), unit.getTitleBn(), unit.getOrdinal()));

				for (Lesson lesson : lessonRepo.findByUnitIdOrderByOrdinal(unit.getId())) {
					lessonRows.add(new LessonRow(lesson.getId().toString(), unit.getId().toString(),
							lesson.getCode(), lesson.getTitleEn(), lesson.getTitleBn(),
							lesson.getOrdinal()));

					for (Exercise exercise : exerciseRepo.findByLessonIdOrderByOrdinal(lesson.getId())) {
						exerciseRows.add(new ExerciseRow(exercise.getId().toString(),
								lesson.getId().toString(), exercise.getType().name(),
								exercise.getOrdinal(), exercise.getPromptEn(),
								exercise.getPromptBn(), exercise.getMediaRef()));

						for (ExerciseOption opt : optionRepo
								.findByExerciseIdOrderByOrdinal(exercise.getId())) {
							optionRows.add(new ExerciseOptionRow(opt.getId().toString(),
									exercise.getId().toString(), opt.getTextEn(), opt.getTextBn(),
									opt.isCorrect(), opt.getOrdinal()));
						}

						for (ExerciseAnswer ans : answerRepo.findByExerciseId(exercise.getId())) {
							answerRows.add(new ExerciseAnswerRow(ans.getId().toString(),
									exercise.getId().toString(), ans.getAcceptedAnswer(),
									ans.isPrimary()));
						}

						for (Hint hint : hintRepo.findByExerciseId(exercise.getId())) {
							hintRows.add(new HintRow(hint.getId().toString(),
									exercise.getId().toString(), hint.getTrigger().name(),
									hint.getTriggerKey(), hint.getTextEn(), hint.getTextBn()));
						}
					}
				}
			}
		}

		return new CurriculumExport(levelRows, unitRows, lessonRows, exerciseRows, optionRows,
				answerRows, hintRows);
	}

	// --- Export row shapes: field-for-field mirror of android LocalXxx Room entities (OF1). ---

	record VocabularyRow(String id, String headword, String senseLabel, String partOfSpeech,
			String cefrLevel, String bnGloss, String exampleEn, String exampleBn, int ordinal) {
	}

	record LevelRow(String id, String code, String titleEn, String titleBn, int ordinal) {
	}

	record UnitRow(String id, String levelId, String code, String titleEn, String titleBn,
			int ordinal) {
	}

	record LessonRow(String id, String unitId, String code, String titleEn, String titleBn,
			int ordinal) {
	}

	record ExerciseRow(String id, String lessonId, String type, int ordinal, String promptEn,
			String promptBn, String mediaRef) {
	}

	record ExerciseOptionRow(String id, String exerciseId, String textEn, String textBn,
			boolean isCorrect, int ordinal) {
	}

	record ExerciseAnswerRow(String id, String exerciseId, String acceptedAnswer,
			boolean isPrimary) {
	}

	record HintRow(String id, String exerciseId, String trigger, String triggerKey,
			String textEn, String textBn) {
	}

	record CurriculumExport(List<LevelRow> levels, List<UnitRow> units, List<LessonRow> lessons,
			List<ExerciseRow> exercises, List<ExerciseOptionRow> exerciseOptions,
			List<ExerciseAnswerRow> exerciseAnswers, List<HintRow> hints) {
	}
}
