package com.shikhi.practice.plan;

import com.shikhi.content.domain.Vocabulary;
import com.shikhi.content.repo.VocabularyRepository;
import com.shikhi.practice.service.PracticeSessionService;
import com.shikhi.practice.service.PracticeWordPicker;
import com.shikhi.progress.service.ProgressService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns today's {@link DailyLearningPlan} into one round's worth of vocabulary, in the plan's
 * bucket-mixed serve order (doc 42 §8, doc 43 §3 VE4). Consumes pending plan items sequentially,
 * tops up with free practice (previously-seen words only, doc 42 §8.6) once the plan is
 * exhausted, and signals "nothing usable" via an empty {@link Optional} so
 * {@code PracticeSessionService} can fall back to the legacy picker — doc 43 §1.4: planner
 * failure must never block learning.
 *
 * <p>{@code composeRound} runs in its own {@code REQUIRES_NEW} transaction (doc 43 §1.4 fix),
 * never joining the caller's round transaction. Joining the caller's transaction (the original
 * behavior) meant any {@code RuntimeException} escaping this method — including an
 * optimistic-lock conflict from a second device racing the same plan header — marked the
 * *shared* transaction rollback-only before the caller's {@code catch} ever ran; the legacy
 * fallback then died at commit with {@code UnexpectedRollbackException} instead of actually
 * serving a round, and a generator failure after items were consumed would silently commit that
 * consumption anyway (today's plan words lost, nothing served). With {@code REQUIRES_NEW}, item
 * consumption, counter decrements, and plan completion commit or roll back as one unit that is
 * entirely independent of the caller: a failure in here (lock conflict or otherwise) surfaces at
 * *this* method's own commit, wrapped into the ordinary exception {@code
 * PracticeSessionService.generateRound} catches, and the outer round transaction stays clean —
 * the legacy fallback is therefore actually reachable.
 *
 * <p>{@link DailyPlanService#getOrCreateToday} is itself {@code REQUIRES_NEW} and briefly
 * suspends this method's transaction while it runs (VE4 fix, see its javadoc) so plan *creation*
 * commits independently too. That nesting is harmless — each of the three transactions
 * (creation, composition, the caller's round) still commits or rolls back on its own. The plan
 * {@code getOrCreateToday} hands back is detached with respect to this method's own transaction
 * — every subsequent read/write here reloads the plan by id to get a managed instance before
 * mutating it.
 */
@Service
public class PlanRoundComposer {

	private final DailyPlanService dailyPlanService;
	private final DailyLearningPlanRepository plans;
	private final DailyLearningPlanItemRepository items;
	private final PracticeWordPicker picker;
	private final VocabularyRepository vocabulary;
	private final ProgressService progress;
	private final Clock clock;
	private final Counter freePracticeWordsCounter;

	public PlanRoundComposer(DailyPlanService dailyPlanService, DailyLearningPlanRepository plans,
			DailyLearningPlanItemRepository items, PracticeWordPicker picker,
			VocabularyRepository vocabulary, ProgressService progress, Clock clock,
			MeterRegistry meterRegistry) {
		this.dailyPlanService = dailyPlanService;
		this.plans = plans;
		this.items = items;
		this.picker = picker;
		this.vocabulary = vocabulary;
		this.progress = progress;
		this.clock = clock;
		this.freePracticeWordsCounter = Counter.builder("shikhi.planner.freepractice.words")
				.description("Words served by free-practice top-up once the daily plan is exhausted")
				.register(meterRegistry);
	}

	/**
	 * Composes one round: up to {@code roundSize} words, plan items first (in serve order),
	 * then a free-practice top-up if the plan came up short. Returns {@link Optional#empty()}
	 * when nothing usable could be produced at all — the caller must fall back to the legacy
	 * picker in that case, never surface an empty round to the learner.
	 *
	 * <p>{@code REQUIRES_NEW} (see the class javadoc): this transaction's fate — commit or
	 * rollback — is entirely this method's own, never entangled with the caller's round
	 * transaction or with exercise generation that happens after this returns.
	 *
	 * <p>{@code pending} is loaded in full (not capped to {@code roundSize}) rather than paged:
	 * {@link #topUpWithFreePractice} needs every remaining PENDING item's vocabulary id to
	 * exclude same-day plan duplicates from free practice (doc 43 §1.4 fix), and a plan's total
	 * item count is already bounded by {@code dailyCapacity} (eager materialization, doc 43
	 * deviation #3 — typically ~100 rows), not the unbounded backlog doc 42 §7.5 warns against.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<List<Vocabulary>> composeRound(UUID userId, UUID sessionId, Set<UUID> usedIds,
			int roundSize) {
		DailyLearningPlan today = dailyPlanService.getOrCreateToday(userId);

		List<DailyLearningPlanItem> pending = items
				.findByPlanIdAndStatusOrderBySequence(today.getId(), ItemStatus.PENDING);

		List<DailyLearningPlanItem> taken = new ArrayList<>();
		List<UUID> orderedIds = new ArrayList<>();
		for (DailyLearningPlanItem item : pending) {
			if (taken.size() == roundSize) {
				break;
			}
			if (usedIds.contains(item.getVocabularyId())) {
				continue;
			}
			taken.add(item);
			orderedIds.add(item.getVocabularyId());
		}

		if (!taken.isEmpty()) {
			consumePlanItems(today.getId(), sessionId, taken);
		}

		if (orderedIds.size() < roundSize) {
			topUpWithFreePractice(userId, usedIds, orderedIds, pending, roundSize);
		}

		if (orderedIds.isEmpty()) {
			return Optional.empty();
		}

		List<Vocabulary> ordered = resolveInOrder(orderedIds);
		return ordered.isEmpty() ? Optional.empty() : Optional.of(ordered);
	}

	/**
	 * Stamps taken items COMPLETED, decrements the plan's matching bucket counters, and closes
	 * the plan out once every bucket has been fully served (doc 42 §9.4 status lifecycle).
	 */
	private void consumePlanItems(UUID planId, UUID sessionId, List<DailyLearningPlanItem> taken) {
		DailyLearningPlan managed = plans.findById(planId).orElseThrow();
		Instant now = clock.instant();
		for (DailyLearningPlanItem item : taken) {
			item.consume(sessionId, now);
			managed.consume(item.getBucket());
		}
		items.saveAll(taken);
		if (managed.getRemainingNew() == 0 && managed.getRemainingWeak() == 0
				&& managed.getRemainingReview() == 0) {
			managed.complete(now);
		}
		plans.save(managed);
	}

	/**
	 * Free practice (doc 42 §8.6): fills the shortfall with previously-SEEN words only, never
	 * unseen — {@code bands} covers every band up to and including the learner's current one
	 * (same ordering {@code DailyPlanService} uses for plan creation).
	 *
	 * <p>Also excludes every word still sitting in {@code pending} (doc 43 §1.4 fix): a
	 * WEAK/REVIEW word later in the plan already has a {@code practice_word_progress} row, so
	 * without this it could be served here via free practice and then served *again* later the
	 * same day when its plan slot comes up — a same-day duplicate that also splices the bucket-
	 * mixed sequence the plan baked in at creation time.
	 */
	private void topUpWithFreePractice(UUID userId, Set<UUID> usedIds, List<UUID> orderedIds,
			List<DailyLearningPlanItem> pending, int roundSize) {
		int shortfall = roundSize - orderedIds.size();
		String currentBand = progress.getState(userId).cefrLevel();
		List<String> bands = PracticeSessionService.BAND_ORDER
				.subList(0, PracticeSessionService.BAND_ORDER.indexOf(currentBand) + 1);

		Set<UUID> exclude = new HashSet<>(usedIds);
		exclude.addAll(orderedIds);
		pending.forEach(item -> exclude.add(item.getVocabularyId()));
		List<Vocabulary> seen = picker.seenWords(userId, bands, exclude, shortfall);
		seen.forEach(word -> orderedIds.add(word.getId()));
		if (!seen.isEmpty()) {
			freePracticeWordsCounter.increment(seen.size());
		}
	}

	/** {@code findAllById} gives no ordering guarantee — restore the plan's serve order. */
	private List<Vocabulary> resolveInOrder(List<UUID> orderedIds) {
		Map<UUID, Vocabulary> byId = vocabulary.findAllById(orderedIds).stream()
				.collect(Collectors.toMap(Vocabulary::getId, word -> word));
		List<Vocabulary> ordered = new ArrayList<>(orderedIds.size());
		for (UUID id : orderedIds) {
			Vocabulary word = byId.get(id);
			if (word != null) {
				ordered.add(word);
			}
		}
		return ordered;
	}
}
