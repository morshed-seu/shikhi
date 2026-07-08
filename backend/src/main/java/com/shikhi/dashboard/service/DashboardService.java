package com.shikhi.dashboard.service;

import com.shikhi.dashboard.repo.DashboardQueryRepository;
import com.shikhi.dashboard.repo.DashboardQueryRepository.LifetimeTotals;
import com.shikhi.dashboard.web.DashboardResponse;
import com.shikhi.dashboard.web.WordMasteryEntry;
import com.shikhi.practice.service.PracticeStatsService;
import com.shikhi.progress.service.ProgressService;
import com.shikhi.review.service.ReviewService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes the learner dashboard snapshot (LLD §2.9, contract {@code DashboardResponse},
 * E13) purely from other modules' read services and the sanctioned reporting seam — {@code
 * dashboard} is a top-of-graph read-only composer, like {@code learning}, and never writes.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

	private final ProgressService progress;
	private final PracticeStatsService practiceStats;
	private final ReviewService review;
	private final DashboardQueryRepository queries;

	public DashboardService(ProgressService progress, PracticeStatsService practiceStats,
			ReviewService review, DashboardQueryRepository queries) {
		this.progress = progress;
		this.practiceStats = practiceStats;
		this.review = review;
		this.queries = queries;
	}

	public DashboardResponse snapshot(UUID userId) {
		List<WordMasteryEntry> wordMastery = practiceStats.masteryByBand(userId).stream()
				.map(band -> new WordMasteryEntry(band.cefrLevel(), (int) band.mastered(),
						(int) band.total()))
				.toList();
		LifetimeTotals totals = queries.lifetimeTotals(userId);

		return new DashboardResponse(progress.getState(userId), wordMastery,
				review.dueCount(userId), (int) queries.lessonsCompleted(userId),
				(int) queries.practiceSessionsCompleted(userId), (int) totals.answered(),
				(int) totals.correct());
	}
}
