package com.shikhi.practice.service;

import com.shikhi.content.repo.VocabularyRepository;
import com.shikhi.practice.repo.PracticeWordProgressRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only per-band word-mastery counts — the {@code practice} seam for the dashboard
 * (LLD §2.9, E13; contract {@code WordMasteryEntry[]}). "Mastered" uses the same semantics as
 * level-up eligibility: {@code times_correct > 0}, via
 * {@link PracticeWordProgressRepository#countMasteredInBand}. Band order mirrors
 * {@link PracticeSessionService#BAND_ORDER}.
 */
@Service
public class PracticeStatsService {

	private final PracticeWordProgressRepository wordProgress;
	private final VocabularyRepository vocabulary;

	public PracticeStatsService(PracticeWordProgressRepository wordProgress,
			VocabularyRepository vocabulary) {
		this.wordProgress = wordProgress;
		this.vocabulary = vocabulary;
	}

	/** Per-band (mastered, total) word counts, ordered A1..C1. */
	@Transactional(readOnly = true)
	public List<BandMastery> masteryByBand(UUID userId) {
		return PracticeSessionService.BAND_ORDER.stream()
				.map(band -> new BandMastery(band, wordProgress.countMasteredInBand(userId, band),
						vocabulary.countByCefrLevel(band)))
				.toList();
	}

	/** One CEFR band's word-mastery counts (mirrors contract {@code WordMasteryEntry}). */
	public record BandMastery(String cefrLevel, long mastered, long total) {
	}
}
