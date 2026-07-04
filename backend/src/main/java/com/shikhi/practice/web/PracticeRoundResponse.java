package com.shikhi.practice.web;

import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.domain.PracticeSession;
import java.util.List;
import java.util.UUID;

/** Contract {@code PracticeRound} — one round of generated exercises plus session context. */
public record PracticeRoundResponse(UUID sessionId, int round, String cefrLevel,
		boolean levelUpEligible, List<PracticeExerciseView> exercises) {

	public static PracticeRoundResponse of(PracticeSession session,
			List<PracticeExercise> exercises, boolean levelUpEligible) {
		return new PracticeRoundResponse(session.getId(), session.getRoundsPlayed(),
				session.getCefrLevel(), levelUpEligible,
				exercises.stream().map(PracticeExerciseView::from).toList());
	}
}
