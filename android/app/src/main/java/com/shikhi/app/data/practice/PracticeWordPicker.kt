package com.shikhi.app.data.practice

import com.shikhi.app.data.content.db.ContentReadDao
import com.shikhi.app.data.content.db.LocalVocabulary
import com.shikhi.app.data.db.WordProgressDao
import javax.inject.Inject
import kotlin.random.Random

/** Baseline mastery assumed for words with no local row yet — must match [WordProgressEngine]. */
const val UNSEEN_MASTERY = 2

/**
 * Kotlin port of the backend's `PracticeWordPicker`
 * (`backend/src/main/java/com/shikhi/practice/service/PracticeWordPicker.java`, docs/
 * 93-offline-learning-design.md §4.3): weakest-first pick, ties randomized.
 *
 * The backend does this in one native-SQL round trip (a join between `vocabulary` and
 * `practice_word_progress`). That join isn't available here: bundled vocabulary lives in
 * [ContentReadDao]'s [com.shikhi.app.data.content.db.ContentDatabase] and local mastery lives in
 * [WordProgressDao]'s [com.shikhi.app.data.db.ShikhiDatabase] — two separate Room databases (§3.2
 * — deliberately split so the answer-key tables never sit next to UI-facing DAOs), and Room has no
 * cross-database join support. This class reproduces the same `ORDER BY COALESCE(mastery_score,
 * 2), RANDOM()` behavior in Kotlin instead: fetch each side separately, then sort by
 * `(mastery ?: 2, random tiebreak)` — behaviorally equivalent selection order, just computed
 * client-side rather than in SQL.
 */
class PracticeWordPicker(
	private val contentDao: ContentReadDao,
	private val wordProgressDao: WordProgressDao,
	private val random: Random,
) {

	/** Hilt-visible constructor: Dagger doesn't resolve Kotlin constructor default values (and a
	 * default value on the primary constructor's [random] param would make this call ambiguous
	 * with it), so the [Random]-taking primary constructor above (used directly by tests for
	 * determinism) takes no default, and this fixed two-arg constructor is the only zero-`Random`
	 * entry point — mirroring the backend generator's package-private-`Random`-constructor /
	 * public-no-arg-constructor split. */
	@Inject constructor(contentDao: ContentReadDao, wordProgressDao: WordProgressDao) :
		this(contentDao, wordProgressDao, Random.Default)

	/** Weakest-first pick of [limit] words from [bands], excluding [usedIds]. */
	suspend fun pick(userId: String, bands: Collection<String>, usedIds: Collection<String>, limit: Int): List<LocalVocabulary> {
		if (limit <= 0) return emptyList()
		val candidates = bands.flatMap { contentDao.getVocabularyByLevel(it) }
			.filter { it.id !in usedIds }
		return weakestFirst(userId, candidates, limit)
	}

	/** Random same-band words to serve as MCQ distractors (picked fresh per round). */
	suspend fun distractorPool(band: String, limit: Int): List<LocalVocabulary> =
		contentDao.getVocabularyByLevel(band).shuffled(random).take(limit)

	private suspend fun weakestFirst(userId: String, candidates: List<LocalVocabulary>, limit: Int): List<LocalVocabulary> {
		if (candidates.isEmpty()) return emptyList()
		val mastery = wordProgressDao.getWordProgressFor(userId, candidates.map { it.id })
			.associate { it.vocabularyId to it.masteryScore }
		return candidates
			.map { word -> Triple(word, mastery[word.id] ?: UNSEEN_MASTERY, random.nextDouble()) }
			.sortedWith(compareBy({ it.second }, { it.third }))
			.take(limit)
			.map { it.first }
	}
}
