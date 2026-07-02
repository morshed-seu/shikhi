package com.shikhi.content.web;

import com.shikhi.content.service.CurriculumProgressOverlay;
import com.shikhi.content.service.CurriculumQueryService;
import com.shikhi.identity.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner-facing content reads (contract {@code /curriculum}, {@code /lessons/{id}}). */
@RestController
@RequestMapping("/v1")
public class CurriculumController {

	private final CurriculumQueryService curriculum;
	private final CurriculumProgressOverlay progressOverlay;

	public CurriculumController(CurriculumQueryService curriculum,
			CurriculumProgressOverlay progressOverlay) {
		this.curriculum = curriculum;
		this.progressOverlay = progressOverlay;
	}

	@GetMapping("/curriculum")
	public CurriculumTree getCurriculum(@AuthenticationPrincipal AuthenticatedUser principal) {
		// The content tree is shared/cached; the learner's progress overlay is applied per request.
		return progressOverlay.overlay(principal.id(), curriculum.getPublishedTree());
	}

	@GetMapping("/lessons/{lessonId}")
	public LessonView getLesson(@PathVariable UUID lessonId) {
		return curriculum.getLesson(lessonId);
	}
}
