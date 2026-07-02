package com.shikhi.content.web;

import java.util.List;
import java.util.UUID;

/**
 * The published curriculum map (contract {@code CurriculumTree}). Per-learner progress
 * ({@code status}) and unlocking ({@code locked}) are filled in from M4; for M2 they are
 * NOT_STARTED / unlocked defaults.
 */
public record CurriculumTree(String contentVersion, List<LevelNode> levels) {

	public record LevelNode(UUID id, String code, Bilingual title, int ordinal,
			List<UnitNode> units) {
	}

	public record UnitNode(UUID id, String code, Bilingual title, int ordinal, boolean locked,
			List<LessonNode> lessons) {
	}

	public record LessonNode(UUID id, Bilingual title, int ordinal, String status,
			boolean locked) {
	}
}
