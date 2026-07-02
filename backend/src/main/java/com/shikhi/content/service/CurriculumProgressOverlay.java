package com.shikhi.content.service;

import com.shikhi.content.web.CurriculumTree;
import java.util.UUID;

/**
 * Applies a learner's progress (lesson status + unlocking) onto the shared, content-only
 * curriculum tree. Defined here and implemented by the progress module so content stays
 * independent of progress (dependency inversion) — the shared tree can stay globally cached
 * while the per-learner overlay is applied per request.
 */
public interface CurriculumProgressOverlay {

	CurriculumTree overlay(UUID userId, CurriculumTree tree);
}
