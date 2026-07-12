package com.shikhi.practice.service;

import com.shikhi.content.domain.Vocabulary;
import com.shikhi.content.repo.VocabularyRepository;
import com.shikhi.content.web.Bilingual;
import com.shikhi.learning.grading.AnswerNormalizer;
import com.shikhi.platform.error.ApiException;
import com.shikhi.practice.config.PlannerProperties;
import com.shikhi.practice.domain.PracticeAnswer;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.domain.PracticeSession;
import com.shikhi.practice.plan.PlanRoundComposer;
import com.shikhi.practice.repo.PracticeAnswerRepository;
import com.shikhi.practice.repo.PracticeExerciseRepository;
import com.shikhi.practice.repo.PracticeSessionRepository;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.practice.web.PracticeAnswerResult;
import com.shikhi.practice.web.PracticeResultResponse;
import com.shikhi.practice.web.PracticeRoundResponse;
import com.shikhi.practice.web.PracticeVerdict;
import com.shikhi.practice.web.SubmitPracticeAnswerRequest;
import com.shikhi.progress.service.ProgressService;
import com.shikhi.progress.web.Stats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a practice run (E12, LLD §2.8): start pins the learner's CEFR level and
 * serves round 1; each "keep going" generates another round; answers are graded against
 * the stored server-only answer key and update per-word mastery, the review ladder (via
 * {@link WordProgressService}, doc 43 §5 VE2), and hearts/XP/streak (via
 * {@link ProgressService}). Submission and completion are idempotent so client retries are
 * safe (NFR-DI1), mirroring the lesson-session contract.
 *
 * <p>Round composition (doc 43 §3/§4 VE4) has two paths behind {@link PlannerProperties#isEnabled()}:
 * when enabled, {@link PlanRoundComposer} serves words from the learner's daily plan (falling
 * back to free practice once the plan is exhausted) in the plan's own bucket-mixed order — never
 * reshuffled; when disabled, or when the planner produces nothing usable (doc 43 §1.4: planner
 * failure must never block learning), rounds fall back to the legacy weakest-first
 * {@link PracticeWordPicker}, shuffled, exactly as before VE4.
 */
@Service
public class PracticeSessionService {

	private static final Logger log = LoggerFactory.getLogger(PracticeSessionService.class);

	static final int ROUND_SIZE = 10;
	/** Words per round drawn from earlier bands for reinforcement (US-12.4, ~30%). */
	static final int EARLIER_BAND_SHARE = 3;
	/** Level-up suggested once this share of the current band has been answered correctly. */
	static final double LEVEL_UP_THRESHOLD = 0.6;

	/**
	 * Public so {@link PracticeStatsService} (E13 dashboard seam) and
	 * {@code com.shikhi.practice.plan.DailyPlanService} (VE3 daily planner) stay in lockstep —
	 * one band ordering shared by every CEFR-aware consumer, never duplicated.
	 */
	public static final List<String> BAND_ORDER = List.of("A1", "A2", "B1", "B2", "C1");

	private static final Bilingual CORRECT_FEEDBACK = new Bilingual("Correct!", "সঠিক!");

	private final PracticeSessionRepository sessions;
	private final PracticeExerciseRepository exercises;
	private final PracticeAnswerRepository answers;
	private final PracticeWordProgressRepository wordProgress;
	private final PracticeWordPicker picker;
	private final PracticeGenerator generator;
	private final VocabularyRepository vocabulary;
	private final ProgressService progress;
	private final WordProgressService wordProgressService;
	private final PlanRoundComposer planComposer;
	private final PlannerProperties planner;

	public PracticeSessionService(PracticeSessionRepository sessions,
			PracticeExerciseRepository exercises, PracticeAnswerRepository answers,
			PracticeWordProgressRepository wordProgress, PracticeWordPicker picker,
			PracticeGenerator generator, VocabularyRepository vocabulary,
			ProgressService progress, WordProgressService wordProgressService,
			PlanRoundComposer planComposer, PlannerProperties planner) {
		this.sessions = sessions;
		this.exercises = exercises;
		this.answers = answers;
		this.wordProgress = wordProgress;
		this.picker = picker;
		this.generator = generator;
		this.vocabulary = vocabulary;
		this.progress = progress;
		this.wordProgressService = wordProgressService;
		this.planComposer = planComposer;
		this.planner = planner;
	}

	@Transactional
	public PracticeRoundResponse start(UUID userId) {
		String level = progress.getState(userId).cefrLevel();
		PracticeSession session = new PracticeSession(userId, level);
		session.startRound();
		sessions.save(session);
		List<PracticeExercise> round = generateRound(session, Set.of());
		return PracticeRoundResponse.of(session, round, levelUpEligible(userId, level));
	}

	@Transactional
	public PracticeRoundResponse nextRound(UUID userId, UUID sessionId) {
		PracticeSession session = ownedSession(userId, sessionId);
		if (session.isCompleted()) {
			throw ApiException.conflict("SESSION_COMPLETED", "This session is already completed");
		}
		session.startRound();
		Set<UUID> used = exercises.findBySessionId(sessionId).stream()
				.map(PracticeExercise::getVocabularyId)
				.collect(Collectors.toSet());
		List<PracticeExercise> round = generateRound(session, used);
		return PracticeRoundResponse.of(session, round,
				levelUpEligible(userId, session.getCefrLevel()));
	}

	@Transactional
	public PracticeAnswerResult submitAnswer(UUID userId, UUID sessionId,
			SubmitPracticeAnswerRequest request) {
		PracticeSession session = ownedSession(userId, sessionId);

		PracticeExercise exercise = exercises.findById(request.exerciseId())
				.filter(e -> e.getSessionId().equals(sessionId))
				.orElseThrow(() -> ApiException.notFound("Exercise not found"));

		// Idempotent replay: rebuild the verdict without re-grading or double-charging.
		PracticeAnswer existing = answers
				.findByUserIdAndIdempotencyKey(userId, request.idempotencyKey())
				.orElse(null);
		if (existing != null) {
			return new PracticeAnswerResult(verdict(existing.isCorrect(), exercise),
					progress.getState(userId));
		}

		if (session.isCompleted()) {
			throw ApiException.conflict("SESSION_COMPLETED", "This session is already completed");
		}

		boolean correct = grade(exercise, request.answer());
		Stats stats = progress.recordPracticeAnswer(userId, correct);
		session.recordAnswer(correct);
		exercise.markAnswered(correct);
		wordProgressService.recordAnswer(userId, exercise.getVocabularyId(), correct);

		PracticeAnswer answer = new PracticeAnswer(sessionId, userId, exercise.getId(),
				request.idempotencyKey(), correct);
		try {
			answers.saveAndFlush(answer);
		}
		catch (DataIntegrityViolationException race) {
			// Concurrent duplicate for the same key: roll back (no double heart/XP).
			throw ApiException.conflict("DUPLICATE_SUBMISSION", "Answer already submitted");
		}
		return new PracticeAnswerResult(verdict(correct, exercise), stats);
	}

	@Transactional
	public PracticeResultResponse complete(UUID userId, UUID sessionId) {
		PracticeSession session = ownedSession(userId, sessionId);
		if (!session.isCompleted()) {
			session.complete();
		}
		int xpEarned = session.getCorrectCount() * ProgressService.XP_PER_CORRECT;
		return new PracticeResultResponse(session.getCorrectCount(), session.getTotalCount(),
				session.getRoundsPlayed(), xpEarned,
				levelUpEligible(userId, session.getCefrLevel()), progress.getState(userId));
	}

	// ---- generation -----------------------------------------------------------------------

	/**
	 * Selects round words, then hands them to the generator (shared tail, {@link #buildRound}).
	 * When the planner is enabled (doc 43 §3/§4 VE4), the daily plan supplies the words in its
	 * own bucket-mixed serve order — plan-backed rounds are never shuffled (doc 42 §8.3: the
	 * mix decided at plan creation is the serve order). A planner failure, or the planner
	 * producing nothing usable (plan exhausted with no seen words left for free practice
	 * either), falls through to the legacy path so a broken planner never blocks learning
	 * (doc 43 §1.4). With the flag off, this is byte-for-byte the pre-VE4 legacy path.
	 *
	 * <p>{@code buildRound} for the plan-backed path runs OUTSIDE the try block, on purpose
	 * (doc 43 §1.4 fix): {@code composeRound} is {@code REQUIRES_NEW} (see its javadoc) and has
	 * already committed its own item consumption by the time it returns, independent of this
	 * transaction. If {@code buildRound}/exercise generation then failed while still inside the
	 * try, the catch below would silently fall back to legacy and re-serve those already-
	 * consumed plan words — quietly discarding them from today's plan for nothing. Letting a
	 * post-composition failure propagate is louder but correct; only a {@code composeRound}
	 * failure (nothing committed yet) is a legitimate reason to fall back.
	 */
	private List<PracticeExercise> generateRound(PracticeSession session, Set<UUID> usedIds) {
		if (planner.isEnabled()) {
			Optional<List<Vocabulary>> planned = Optional.empty();
			try {
				planned = planComposer.composeRound(
						session.getUserId(), session.getId(), usedIds, ROUND_SIZE);
			}
			catch (Exception e) {
				log.warn("Planner failed to compose a round for session {}; "
						+ "falling back to the legacy picker", session.getId(), e);
			}
			if (planned.isPresent()) {
				return buildRound(session, planned.get(), false);
			}
		}
		return buildRound(session, legacyPick(session, usedIds), true);
	}

	/**
	 * Legacy word selection (pre-VE4, unchanged): mostly the session's band, a few from
	 * earlier bands (US-12.4), topped up from all eligible bands if the band-restricted pick
	 * comes up short. Words already used in this session are excluded; if the exclusion
	 * exhausts the pool the round simply comes up shorter.
	 */
	private List<Vocabulary> legacyPick(PracticeSession session, Set<UUID> usedIds) {
		String current = session.getCefrLevel();
		List<String> earlier = BAND_ORDER.subList(0, BAND_ORDER.indexOf(current));

		int earlierCount = earlier.isEmpty() ? 0 : EARLIER_BAND_SHARE;
		List<Vocabulary> words = new ArrayList<>(
				picker.pick(session.getUserId(), List.of(current), usedIds,
						ROUND_SIZE - earlierCount));
		if (earlierCount > 0) {
			words.addAll(picker.pick(session.getUserId(), earlier, usedIds, earlierCount));
		}
		if (words.size() < ROUND_SIZE) {
			// Band(s) nearly exhausted for this session: top up from all eligible bands.
			Set<UUID> exclude = new HashSet<>(usedIds);
			words.forEach(w -> exclude.add(w.getId()));
			List<String> allBands = new ArrayList<>(earlier);
			allBands.add(current);
			words.addAll(picker.pick(session.getUserId(), allBands, exclude,
					ROUND_SIZE - words.size()));
		}
		return words;
	}

	/**
	 * Shared tail for both selection paths: shuffle (legacy only — plan-backed order is
	 * preserved as-is), build distractor pools, generate exercises, persist. Empty input is a
	 * hard failure either way — the caller must have already fallen back if a source produced
	 * nothing.
	 */
	private List<PracticeExercise> buildRound(PracticeSession session, List<Vocabulary> words,
			boolean shuffle) {
		if (words.isEmpty()) {
			throw ApiException.conflict("NO_VOCABULARY",
					"No vocabulary is available for this level");
		}
		List<Vocabulary> ordered = words;
		if (shuffle) {
			ordered = new ArrayList<>(words);
			Collections.shuffle(ordered);
		}

		Map<String, List<Vocabulary>> pools = ordered.stream()
				.map(Vocabulary::getCefrLevel)
				.distinct()
				.collect(Collectors.toMap(band -> band, band -> picker.distractorPool(band, 40)));

		List<PracticeExercise> round = generator.generateRound(session.getId(),
				session.getRoundsPlayed(), ordered, pools);
		return exercises.saveAll(round);
	}

	// ---- grading (against the server-only answer key) --------------------------------------

	private boolean grade(PracticeExercise exercise, Map<String, Object> answer) {
		Map<String, Object> key = exercise.getAnswerKey();
		return switch (exercise.getType()) {
			case WORD_MEANING, MEANING_WORD, SENTENCE_GAP -> {
				String selected = asString(answer.get("selectedOptionId"));
				yield selected.equals(asString(key.get("correctOptionId")));
			}
			case SENTENCE_BUILD -> {
				if (!(answer.get("tokenOrder") instanceof List<?> tokens)) {
					yield false;
				}
				String assembled = AnswerNormalizer.normalize(tokens.stream()
						.map(this::asString)
						.collect(Collectors.joining(" ")));
				yield acceptedAnswers(key).stream()
						.map(AnswerNormalizer::normalize)
						.anyMatch(assembled::equals);
			}
			case TYPE_WORD -> {
				String submitted = AnswerNormalizer.normalize(asString(answer.get("text")));
				yield acceptedAnswers(key).stream()
						.map(AnswerNormalizer::normalize)
						.anyMatch(submitted::equals);
			}
		};
	}

	/** Wrong answers reveal the right one — the moment of learning (US-12.5). */
	private PracticeVerdict verdict(boolean correct, PracticeExercise exercise) {
		if (correct) {
			return new PracticeVerdict(true, CORRECT_FEEDBACK);
		}
		String reveal = asString(exercise.getAnswerKey().get("revealText"));
		return new PracticeVerdict(false, new Bilingual(
				"Correct answer: " + reveal, "সঠিক উত্তর: " + reveal));
	}

	private List<String> acceptedAnswers(Map<String, Object> key) {
		return key.get("accepted") instanceof List<?> list
				? list.stream().map(this::asString).toList()
				: List.of();
	}

	private boolean levelUpEligible(UUID userId, String cefrLevel) {
		if (BAND_ORDER.getLast().equals(cefrLevel)) {
			return false;
		}
		long bandSize = vocabulary.countByCefrLevel(cefrLevel);
		return bandSize > 0
				&& wordProgress.countMasteredInBand(userId, cefrLevel) >= bandSize * LEVEL_UP_THRESHOLD;
	}

	private PracticeSession ownedSession(UUID userId, UUID sessionId) {
		PracticeSession session = sessions.findById(sessionId)
				.orElseThrow(() -> ApiException.notFound("Session not found"));
		if (!session.getUserId().equals(userId)) {
			// Don't reveal another learner's session exists.
			throw ApiException.notFound("Session not found");
		}
		return session;
	}

	private String asString(Object value) {
		return value == null ? "" : value.toString();
	}
}
