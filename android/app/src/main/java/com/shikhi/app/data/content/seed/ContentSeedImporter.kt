package com.shikhi.app.data.content.seed

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.room.withTransaction
import com.shikhi.app.data.content.db.ContentDatabase
import com.shikhi.app.data.content.db.LocalExercise
import com.shikhi.app.data.content.db.LocalExerciseAnswer
import com.shikhi.app.data.content.db.LocalExerciseOption
import com.shikhi.app.data.content.db.LocalHint
import com.shikhi.app.data.content.db.LocalLesson
import com.shikhi.app.data.content.db.LocalLevel
import com.shikhi.app.data.content.db.LocalUnit
import com.shikhi.app.data.content.db.LocalVocabulary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bump when `assets/content-seed/{vocabulary,curriculum}.json` is regenerated with content that
 * must reach devices that already imported an earlier seed (OF1,
 * docs/93-offline-learning-design.md §6). [ContentSeedImporter] compares this against the
 * version recorded in [sessionDataStore] and re-imports only when it's higher.
 */
const val CONTENT_SEED_VERSION: Int = 1

private val CONTENT_SEED_VERSION_KEY = intPreferencesKey("content_seed_version")

private const val VOCABULARY_ASSET_PATH = "content-seed/vocabulary.json"
private const val CURRICULUM_ASSET_PATH = "content-seed/curriculum.json"

/**
 * One-time importer: reads the bundled `assets/content-seed/` JSON files and bulk-inserts into
 * [ContentDatabase] inside a single transaction. Zero network calls (OF1 goal — this is pure
 * asset I/O + local DB writes).
 *
 * OF2 invokes this from `ContentSeedWorker`, a `WorkManager` one-time worker scheduled at app
 * startup (mirrors `OutboxSyncWorker`'s registration). `@Inject constructor` + `@Singleton`
 * makes it Hilt-providable without any `AppModule` boilerplate, while staying just as
 * unit-testable with fakes/an in-memory [ContentDatabase] and a `DataStore<Preferences>` test
 * double (see `ContentSeedImporterTest`, which constructs it directly, bypassing Hilt).
 */
@Singleton
class ContentSeedImporter @Inject constructor(
	@param:ApplicationContext private val context: Context,
	private val database: ContentDatabase,
	private val sessionDataStore: DataStore<Preferences>,
) {

	private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

	/**
	 * Imports the bundled seed if it hasn't been imported yet at [CONTENT_SEED_VERSION] or
	 * later. Returns `true` if an import ran, `false` if it was skipped as already current.
	 */
	suspend fun importIfNeeded(): Boolean {
		val importedVersion = sessionDataStore.data.first()[CONTENT_SEED_VERSION_KEY] ?: 0
		if (importedVersion >= CONTENT_SEED_VERSION) {
			return false
		}

		val vocabulary = readAsset(VOCABULARY_ASSET_PATH) {
			json.decodeFromString(ListSerializer(VocabularySeedDto.serializer()), it)
		}
		val curriculum = readAsset(CURRICULUM_ASSET_PATH) {
			json.decodeFromString(CurriculumSeedDto.serializer(), it)
		}

		database.withTransaction {
			val readDao = database.contentReadDao()
			val answerKeyDao = database.contentAnswerKeyDao()

			readDao.insertVocabulary(vocabulary.map { it.toEntity() })
			readDao.insertLevels(curriculum.levels.map { it.toEntity() })
			readDao.insertUnits(curriculum.units.map { it.toEntity() })
			readDao.insertLessons(curriculum.lessons.map { it.toEntity() })
			readDao.insertExercises(curriculum.exercises.map { it.toEntity() })
			answerKeyDao.insertOptions(curriculum.exerciseOptions.map { it.toEntity() })
			answerKeyDao.insertAnswers(curriculum.exerciseAnswers.map { it.toEntity() })
			answerKeyDao.insertHints(curriculum.hints.map { it.toEntity() })
		}

		sessionDataStore.edit { prefs -> prefs[CONTENT_SEED_VERSION_KEY] = CONTENT_SEED_VERSION }
		return true
	}

	private inline fun <T> readAsset(path: String, parse: (String) -> T): T {
		val text = context.assets.open(path).use(InputStream::readBytes).toString(Charsets.UTF_8)
		return parse(text)
	}
}

private fun VocabularySeedDto.toEntity() = LocalVocabulary(
	id = id,
	headword = headword,
	senseLabel = senseLabel,
	partOfSpeech = partOfSpeech,
	cefrLevel = cefrLevel,
	bnGloss = bnGloss,
	exampleEn = exampleEn,
	exampleBn = exampleBn,
	ordinal = ordinal,
)

private fun LevelSeedDto.toEntity() = LocalLevel(
	id = id,
	code = code,
	titleEn = titleEn,
	titleBn = titleBn,
	ordinal = ordinal,
)

private fun UnitSeedDto.toEntity() = LocalUnit(
	id = id,
	levelId = levelId,
	code = code,
	titleEn = titleEn,
	titleBn = titleBn,
	ordinal = ordinal,
)

private fun LessonSeedDto.toEntity() = LocalLesson(
	id = id,
	unitId = unitId,
	code = code,
	titleEn = titleEn,
	titleBn = titleBn,
	ordinal = ordinal,
)

private fun ExerciseSeedDto.toEntity() = LocalExercise(
	id = id,
	lessonId = lessonId,
	type = type,
	ordinal = ordinal,
	promptEn = promptEn,
	promptBn = promptBn,
	mediaRef = mediaRef,
)

private fun ExerciseOptionSeedDto.toEntity() = LocalExerciseOption(
	id = id,
	exerciseId = exerciseId,
	textEn = textEn,
	textBn = textBn,
	isCorrect = isCorrect,
	ordinal = ordinal,
)

private fun ExerciseAnswerSeedDto.toEntity() = LocalExerciseAnswer(
	id = id,
	exerciseId = exerciseId,
	acceptedAnswer = acceptedAnswer,
	isPrimary = isPrimary,
)

private fun HintSeedDto.toEntity() = LocalHint(
	id = id,
	exerciseId = exerciseId,
	trigger = trigger,
	triggerKey = triggerKey,
	textEn = textEn,
	textBn = textBn,
)
