package com.shikhi.practice.service;

import com.shikhi.practice.config.PlannerProperties;
import com.shikhi.practice.domain.PracticeWordProgress;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import com.shikhi.practice.schedule.ReviewProgress;
import com.shikhi.practice.schedule.ReviewProgressRepository;
import com.shikhi.practice.schedule.WordReviewScheduler;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Updates one learner's word-level state after an answer is graded (doc 43 §3/§5/§6 VE2):
 * mastery (0..5, {@link PracticeWordProgress}), graduation onto the review ladder, and ladder
 * transitions ({@link ReviewProgress}) — all in a single method so a caller's transaction
 * covers the whole update atomically. Extracted from
 * {@code PracticeSessionService.recordWordProgress} (doc 43 §5 module placement).
 *
 * <p>Ladder rule (doc 43 §3, deviation #7): a correct answer only promotes the ladder when the
 * word was actually due — this method is invoked on every answer, including ones served from
 * the New/Weak buckets while a word is not yet due, and those must not inflate future
 * intervals. A late review (answered well past {@code dueAt}) still promotes normally; a wrong
 * answer always demotes, regardless of due status.
 */
@Service
public class WordProgressService {

	private final PracticeWordProgressRepository wordProgressRepository;
	private final ReviewProgressRepository reviewProgressRepository;
	private final WordReviewScheduler scheduler;
	private final PlannerProperties planner;
	private final Clock clock;

	public WordProgressService(PracticeWordProgressRepository wordProgressRepository,
			ReviewProgressRepository reviewProgressRepository, WordReviewScheduler scheduler,
			PlannerProperties planner, Clock clock) {
		this.wordProgressRepository = wordProgressRepository;
		this.reviewProgressRepository = reviewProgressRepository;
		this.scheduler = scheduler;
		this.planner = planner;
		this.clock = clock;
	}

	@Transactional
	public void recordAnswer(UUID userId, UUID vocabularyId, boolean correct) {
		// Computed once and reused for both mastery and ladder updates (doc 43 §4 Fix 5) — a
		// single instant for the whole method, not two independent reads of the clock.
		Instant now = clock.instant();

		PracticeWordProgress mastery = wordProgressRepository
				.findById(new PracticeWordProgress.Key(userId, vocabularyId))
				.orElseGet(() -> new PracticeWordProgress(userId, vocabularyId));
		mastery.recordAnswer(correct, now);
		wordProgressRepository.save(mastery);

		ReviewProgress.Key reviewKey = new ReviewProgress.Key(userId, vocabularyId);
		ReviewProgress review = reviewProgressRepository.findById(reviewKey).orElse(null);

		if (review == null) {
			// Graduation only rewards a correct answer (doc 43 §4 Fix 4): evaluating the gate
			// after a wrong answer too let a word enter the review ladder off the very mistake
			// the learner just made — reachable once mastery/timesCorrect/timesSeen config
			// thresholds allow it, and educationally backwards either way.
			if (correct && graduated(mastery)) {
				int stage = 1;
				reviewProgressRepository.save(new ReviewProgress(userId, vocabularyId, stage,
						now.plus(scheduler.interval(stage))));
			}
			return;
		}

		if (correct) {
			// Non-due appearance: leave the ladder untouched (deviation #7).
			if (review.isDue(now)) {
				int newStage = review.getReviewStage() + 1;
				review.promote(now, scheduler.interval(newStage));
				reviewProgressRepository.save(review);
			}
		}
		else {
			int newStage = Math.max(0, review.getReviewStage() - 2);
			review.demote(now, scheduler.interval(newStage));
			reviewProgressRepository.save(review);
		}
	}

	/**
	 * Graduation gate (doc 43 §3): all three thresholds, evaluated after this answer — but only
	 * called when the answer was correct (Fix 4); a wrong answer never graduates a word.
	 */
	private boolean graduated(PracticeWordProgress mastery) {
		return mastery.getMasteryScore() >= planner.getGraduationMastery()
				&& mastery.getTimesCorrect() >= planner.getGraduationTimesCorrect()
				&& mastery.getTimesSeen() >= planner.getGraduationTimesSeen();
	}
}
