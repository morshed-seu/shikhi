package com.shikhi.practice.plan;

import com.shikhi.practice.config.PlannerProperties;
import com.shikhi.practice.policy.AdaptiveAllocationPolicy;
import com.shikhi.practice.policy.AllocationPolicy;
import com.shikhi.practice.policy.BucketMixer;
import com.shikhi.practice.policy.BucketTargets;
import com.shikhi.practice.policy.NewCandidate;
import com.shikhi.practice.policy.NewWordSelectionPolicy;
import com.shikhi.practice.policy.Percents;
import com.shikhi.practice.policy.ReviewCandidate;
import com.shikhi.practice.policy.ReviewSelectionPolicy;
import com.shikhi.practice.policy.WeakCandidate;
import com.shikhi.practice.policy.WeakSelectionPolicy;
import com.shikhi.practice.repo.PracticeAnswerRepository;
import com.shikhi.practice.service.PracticeSessionService;
import com.shikhi.progress.service.ProgressService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Daily Learning Planner (doc 42 §6/§10, doc 43 §3 VE3): generates (or loads) today's
 * plan for a learner and eagerly materializes every item in one bucket-mixed serve order.
 * Orchestration only — no ranking logic lives here; that's entirely {@code
 * com.shikhi.practice.policy}'s job (doc 42 §6.6), and no SQL beyond simple lookups (candidate
 * fetching is {@link PlanCandidateRepository}'s job).
 *
 * <p>VE5 (doc 43 §6) adds the adaptive accuracy policy (doc 42 §6.4, deviation #10) and
 * observability (doc 42 §12.2) on top of VE3's plan creation and VE4's round composition wiring.
 */
@Service
public class DailyPlanService {

	private static final Logger log = LoggerFactory.getLogger(DailyPlanService.class);

	/**
	 * Candidate fetch cap as a multiple of daily capacity (doc 42 §10: "cap the fetch at e.g.
	 * 3x target"). Capacity, not the per-bucket target, is used as the base because the
	 * backlog-protection split isn't known until after review candidates are counted.
	 */
	private static final int CANDIDATE_FETCH_MULTIPLIER = 3;

	/** Rolling accuracy window for the adaptive policy (doc 42 §6.4): the trailing 7 days. */
	private static final Duration ROLLING_ACCURACY_WINDOW = Duration.ofDays(7);

	private final DailyLearningPlanRepository plans;
	private final DailyLearningPlanItemRepository items;
	private final PlanCandidateRepository candidates;
	private final PracticeAnswerRepository answers;
	private final ProgressService progress;
	private final PlannerProperties planner;
	private final Clock clock;

	private final AllocationPolicy allocationPolicy = new AllocationPolicy();
	private final AdaptiveAllocationPolicy adaptiveAllocationPolicy = new AdaptiveAllocationPolicy();
	private final ReviewSelectionPolicy reviewSelectionPolicy = new ReviewSelectionPolicy();
	private final WeakSelectionPolicy weakSelectionPolicy = new WeakSelectionPolicy();
	private final NewWordSelectionPolicy newWordSelectionPolicy = new NewWordSelectionPolicy();
	private final BucketMixer bucketMixer = new BucketMixer();

	/**
	 * Postgres aborts the whole transaction on the first failed statement — after the unique-
	 * constraint violation, the connection {@code getOrCreateToday}'s own transaction is bound
	 * to refuses any further statements (see doc 43 §3 VE3 friction notes). The reload after a
	 * lost race therefore runs in a genuinely new transaction/connection (suspending the
	 * broken one), not just a new statement inside the same one.
	 */
	private final TransactionTemplate requiresNewTransaction;

	/**
	 * Micrometer meters (doc 42 §12.2). {@code spring-boot-starter-actuator} is a main
	 * (non-test) dependency, so {@link MeterRegistry} is always available for injection here —
	 * no conditional wiring needed.
	 */
	private final Timer generationTimer;
	private final Counter successCounter;
	private final Counter failureCounter;
	private final DistributionSummary reviewDueSummary;

	public DailyPlanService(DailyLearningPlanRepository plans,
			DailyLearningPlanItemRepository items, PlanCandidateRepository candidates,
			PracticeAnswerRepository answers, ProgressService progress, PlannerProperties planner,
			Clock clock, PlatformTransactionManager transactionManager,
			MeterRegistry meterRegistry) {
		this.plans = plans;
		this.items = items;
		this.candidates = candidates;
		this.answers = answers;
		this.progress = progress;
		this.planner = planner;
		this.clock = clock;
		this.requiresNewTransaction = new TransactionTemplate(transactionManager);
		this.requiresNewTransaction.setPropagationBehavior(
				TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		this.requiresNewTransaction.setReadOnly(true);

		this.generationTimer = Timer.builder("shikhi.planner.generation")
				.description("Time to generate (or load) a learner's daily plan")
				.register(meterRegistry);
		this.successCounter = Counter.builder("shikhi.planner.success")
				.description("Daily plan generations that completed without error")
				.register(meterRegistry);
		this.failureCounter = Counter.builder("shikhi.planner.failure")
				.description("Daily plan generations that failed with an unexpected error")
				.register(meterRegistry);
		this.reviewDueSummary = DistributionSummary.builder("shikhi.planner.review.due")
				.description("Due-review candidate count seen at plan generation time")
				.register(meterRegistry);
	}

	/**
	 * Loads today's plan, creating it on first request of the day (doc 42 §6.1). Runs in its
	 * own {@code REQUIRES_NEW} transaction (doc 43 §3 VE4 fix) rather than joining whatever
	 * transaction the caller is already in: once {@code PracticeSessionService.start}/
	 * {@code nextRound} (VE4) call this from inside their own round transaction, a lost
	 * plan-creation race here would otherwise poison the *caller's* connection (Postgres
	 * aborts the whole transaction on the first failed statement — see the
	 * {@code DataIntegrityViolationException} handling below) and abort the entire round
	 * request just because two devices happened to race on plan creation. Isolating plan
	 * creation in its own transaction means it always commits or rolls back independently of
	 * the round it was called from.
	 *
	 * <p>Idempotent under concurrent callers (two devices racing on the first request): the
	 * unique constraint on {@code (user_id, plan_date)} rejects the loser's insert, which
	 * reloads and returns the winner's plan instead of erroring (doc 42 §6.7, same race
	 * pattern as {@code PracticeSessionService.submitAnswer}).
	 *
	 * <p>Because this method commits (or reloads) in its own transaction before returning, the
	 * {@link DailyLearningPlan} handed back is <strong>detached</strong> with respect to any
	 * transaction the caller is in. A caller that needs a managed instance — e.g. VE4's
	 * {@code PlanRoundComposer}, which decrements {@code remaining*} counters — must reload it
	 * by id inside its own transaction rather than mutate the detached instance directly.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public DailyLearningPlan getOrCreateToday(UUID userId) {
		LocalDate planDate = LocalDate.now(clock);
		return plans.findByUserIdAndPlanDate(userId, planDate)
				.orElseGet(() -> createPlan(userId, planDate));
	}

	/**
	 * Times the whole plan-generation body (doc 42 §12.2) and classifies the outcome: the
	 * unique-constraint race handled inside {@link #doCreatePlan} returns normally (it never
	 * throws past this point) and therefore counts as success, same as a clean first-time
	 * creation — only a genuinely unexpected exception counts as {@code shikhi.planner.failure}.
	 *
	 * <p>One {@link Timer.Sample} owns the duration for both the Micrometer metric and the
	 * completion log line (doc 43 §4 Fix 7e — no separate hand-rolled {@code System.nanoTime()}
	 * stopwatch): {@link #doCreatePlan} stops it itself right where it logs, on the success
	 * path; a failure stops it here instead. Either way {@code stop} runs exactly once.
	 */
	private DailyLearningPlan createPlan(UUID userId, LocalDate planDate) {
		Timer.Sample sample = Timer.start();
		try {
			DailyLearningPlan plan = doCreatePlan(userId, planDate, sample);
			successCounter.increment();
			return plan;
		}
		catch (RuntimeException e) {
			failureCounter.increment();
			sample.stop(generationTimer);
			throw e;
		}
	}

	private DailyLearningPlan doCreatePlan(UUID userId, LocalDate planDate, Timer.Sample sample) {
		String currentBand = progress.getState(userId).cefrLevel();
		List<String> earlierBands = PracticeSessionService.BAND_ORDER
				.subList(0, PracticeSessionService.BAND_ORDER.indexOf(currentBand));
		List<String> allBands = new ArrayList<>(earlierBands);
		allBands.add(currentBand);

		int capacity = planner.getDailyCapacity();
		int fetchLimit = capacity * CANDIDATE_FETCH_MULTIPLIER;
		Instant now = clock.instant();

		List<ReviewCandidate> reviewCandidates = candidates.dueReviews(userId, now, fetchLimit);
		reviewDueSummary.record(reviewCandidates.size());
		List<WeakCandidate> weakCandidates = dedupeAgainstReview(
				candidates.weakWords(userId, allBands, planner.getWeakMasteryThreshold(), fetchLimit),
				reviewCandidates);
		List<NewCandidate> newCandidates = candidates.newWords(userId, allBands, fetchLimit);

		Percents configuredPercents = new Percents(planner.getNewPercent(), planner.getWeakPercent(),
				planner.getReviewPercent());
		RollingAccuracy accuracy = rollingAccuracy(userId, now);
		Percents adjustedPercents = adaptiveAllocationPolicy.adjust(configuredPercents,
				accuracy.value(), accuracy.sampleSize());
		Percents backlogPercents = new Percents(planner.getBacklogNewPercent(),
				planner.getBacklogWeakPercent(), planner.getBacklogReviewPercent());
		BucketTargets targets = allocationPolicy.allocate(capacity, adjustedPercents, backlogPercents,
				reviewCandidates.size(), weakCandidates.size(), newCandidates.size());

		List<ReviewCandidate> selectedReview = reviewSelectionPolicy.select(reviewCandidates,
				targets.reviewTarget());
		List<WeakCandidate> selectedWeak = weakSelectionPolicy.select(weakCandidates,
				targets.weakTarget(), now, currentBand, earlierBands,
				planner.getWeakCurrentBandPercent());
		List<NewCandidate> selectedNew = newWordSelectionPolicy.select(newCandidates,
				targets.newTarget(), currentBand, earlierBands, planner.getNewCurrentBandPercent());

		List<UUID> newIds = selectedNew.stream().map(NewCandidate::vocabularyId).toList();
		List<UUID> weakIds = selectedWeak.stream().map(WeakCandidate::vocabularyId).toList();
		List<UUID> reviewIds = selectedReview.stream().map(ReviewCandidate::vocabularyId).toList();

		List<BucketMixer.MixedItem> mixed = bucketMixer.mix(newIds, weakIds, reviewIds,
				planner.getMaxConsecutiveBucket(), new Random());

		DailyLearningPlan plan = new DailyLearningPlan(userId, planDate, capacity, newIds.size(),
				weakIds.size(), reviewIds.size(), configSnapshot(accuracy, adjustedPercents));

		List<DailyLearningPlanItem> planItems = new ArrayList<>(mixed.size());
		for (int sequence = 0; sequence < mixed.size(); sequence++) {
			BucketMixer.MixedItem m = mixed.get(sequence);
			planItems.add(new DailyLearningPlanItem(plan.getId(), m.vocabularyId(), m.bucket(),
					sequence));
		}

		DailyLearningPlan created;
		try {
			plans.saveAndFlush(plan);
			items.saveAllAndFlush(planItems);
			created = plan;
		}
		catch (DataIntegrityViolationException race) {
			// Another request for the same (userId, planDate) won the race. The insert failure
			// already poisoned this transaction's connection (Postgres refuses further
			// statements until rollback) — but since getOrCreateToday runs in its own
			// REQUIRES_NEW transaction, only that isolated transaction is poisoned, never
			// whatever transaction the caller (e.g. a practice round) is in. The reload below
			// therefore runs in yet another, genuinely separate transaction/connection rather
			// than reusing this poisoned one.
			TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
			created = requiresNewTransaction.execute(status -> plans
					.findByUserIdAndPlanDate(userId, planDate)
					.orElseThrow(() -> race));
		}

		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(sample.stop(generationTimer));
		log.info("Daily plan generated userId={} planDate={} capacity={} new={} weak={} "
						+ "review={} rollingAccuracy={} accuracySampleSize={} elapsedMs={}",
				userId, planDate, capacity, newIds.size(), weakIds.size(), reviewIds.size(),
				accuracy.value(), accuracy.sampleSize(), elapsedMs);
		return created;
	}

	/**
	 * A word due for review belongs to the REVIEW bucket, not WEAK — without this, both
	 * queries could surface the same word and violate {@code UNIQUE(plan_id, vocabulary_id)}
	 * (V22). {@code newWords()} can never collide (see {@link PlanCandidateRepository}).
	 */
	private List<WeakCandidate> dedupeAgainstReview(List<WeakCandidate> weak,
			List<ReviewCandidate> review) {
		Set<UUID> dueIds = review.stream().map(ReviewCandidate::vocabularyId)
				.collect(Collectors.toSet());
		return weak.stream().filter(c -> !dueIds.contains(c.vocabularyId())).toList();
	}

	/**
	 * Rolling accuracy over the trailing 7 days (doc 42 §6.4, doc 43 deviation #10): a learner
	 * with zero answers in the window has {@code value = 0.0} but {@code sampleSize = 0}, which
	 * {@link AdaptiveAllocationPolicy} treats as cold start regardless of the (meaningless)
	 * value.
	 */
	private RollingAccuracy rollingAccuracy(UUID userId, Instant now) {
		Instant since = now.minus(ROLLING_ACCURACY_WINDOW);
		// One round trip for both counts (doc 43 §4 Fix 7c), not two separate COUNTs.
		PracticeAnswerRepository.AccuracyCounts counts = answers.rollingAccuracyCounts(userId, since);
		long sampleSize = counts.getTotalCount();
		if (sampleSize == 0) {
			return new RollingAccuracy(0.0, 0);
		}
		return new RollingAccuracy((double) counts.getCorrectCount() / sampleSize,
				(int) Math.min(sampleSize, Integer.MAX_VALUE));
	}

	private record RollingAccuracy(double value, int sampleSize) {
	}

	/** The configuration values used to build this plan, frozen at creation (doc 42 §9.4). */
	private Map<String, Object> configSnapshot(RollingAccuracy accuracy, Percents adjustedPercents) {
		return Map.ofEntries(
				Map.entry("dailyCapacity", planner.getDailyCapacity()),
				Map.entry("newPercent", planner.getNewPercent()),
				Map.entry("weakPercent", planner.getWeakPercent()),
				Map.entry("reviewPercent", planner.getReviewPercent()),
				Map.entry("weakMasteryThreshold", planner.getWeakMasteryThreshold()),
				Map.entry("newCurrentBandPercent", planner.getNewCurrentBandPercent()),
				Map.entry("weakCurrentBandPercent", planner.getWeakCurrentBandPercent()),
				Map.entry("maxConsecutiveBucket", planner.getMaxConsecutiveBucket()),
				Map.entry("backlogReviewPercent", planner.getBacklogReviewPercent()),
				Map.entry("backlogWeakPercent", planner.getBacklogWeakPercent()),
				Map.entry("backlogNewPercent", planner.getBacklogNewPercent()),
				Map.entry("rollingAccuracy", accuracy.value()),
				Map.entry("accuracySampleSize", accuracy.sampleSize()),
				Map.entry("adjustedNewPercent", adjustedPercents.newPercent()),
				Map.entry("adjustedWeakPercent", adjustedPercents.weakPercent()),
				Map.entry("adjustedReviewPercent", adjustedPercents.reviewPercent()));
	}
}
