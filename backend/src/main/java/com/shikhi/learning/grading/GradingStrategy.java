package com.shikhi.learning.grading;

import com.shikhi.content.domain.ExerciseType;

/**
 * The grading seam (ADR-0006 / D4). {@link GradingService} selects a strategy per exercise
 * type; today only {@link RuleBasedGradingStrategy} exists, but an AI strategy can be added
 * behind this same interface without changing the {@link GradingVerdict} contract.
 */
public interface GradingStrategy {

	boolean supports(ExerciseType type);

	GradingVerdict grade(GradingContext ctx);
}
