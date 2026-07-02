package com.shikhi.review.service;

import com.shikhi.content.domain.Exercise;
import com.shikhi.content.repo.ExerciseRepository;
import com.shikhi.content.web.Bilingual;
import com.shikhi.review.domain.ReviewItem;
import com.shikhi.review.repo.ReviewItemRepository;
import com.shikhi.review.web.ReviewItemView;
import com.shikhi.review.web.ReviewResultsRequest.ReviewResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spaced-repetition review (LLD §2.6). Missed exercises enter the Leitner schedule; the due
 * queue resurfaces them; recall outcomes reschedule them. In M6 review is self-graded recall
 * (the contract's {@code ReviewItem} carries only the prompt), so no server grading is needed.
 */
@Service
public class ReviewService {

	private final ReviewItemRepository items;
	private final ExerciseRepository exercises;

	public ReviewService(ReviewItemRepository items, ExerciseRepository exercises) {
		this.items = items;
		this.exercises = exercises;
	}

	/** A wrong answer in a lesson schedules the exercise for review (box 1). */
	@Transactional
	public void onMissed(UUID userId, UUID exerciseId) {
		ReviewItem item = items.findByUserIdAndExerciseId(userId, exerciseId)
				.orElse(null);
		if (item == null) {
			items.save(new ReviewItem(userId, exerciseId));
		}
		else {
			item.missed();
			items.save(item);
		}
	}

	@Transactional(readOnly = true)
	public List<ReviewItemView> getDue(UUID userId) {
		List<ReviewItemView> due = new ArrayList<>();
		for (ReviewItem item : items.findByUserIdAndDueAtLessThanEqualOrderByDueAt(userId, Instant.now())) {
			// Skip items whose exercise no longer exists (content republished/removed).
			Exercise exercise = exercises.findById(item.getExerciseId()).orElse(null);
			if (exercise != null) {
				due.add(new ReviewItemView(item.getExerciseId(),
						new Bilingual(exercise.getPromptEn(), exercise.getPromptBn()),
						item.getBoxLevel(), item.getDueAt()));
			}
		}
		return due;
	}

	@Transactional
	public void recordResults(UUID userId, List<ReviewResult> results) {
		for (ReviewResult result : results) {
			items.findByUserIdAndExerciseId(userId, result.exerciseId()).ifPresent(item -> {
				if (result.correct()) {
					item.recalled();
				}
				else {
					item.missed();
				}
				items.save(item);
			});
		}
	}

	/** How many review items this learner had scheduled/updated since {@code since}. */
	@Transactional(readOnly = true)
	public int countAddedSince(UUID userId, Instant since) {
		return (int) items.countByUserIdAndUpdatedAtGreaterThanEqual(userId, since);
	}
}
