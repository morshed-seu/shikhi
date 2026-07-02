package com.shikhi.content.web;

import com.shikhi.content.service.CurriculumQueryService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Learner-facing content reads (contract {@code /curriculum}, {@code /lessons/{id}}). */
@RestController
@RequestMapping("/v1")
public class CurriculumController {

	private final CurriculumQueryService curriculum;

	public CurriculumController(CurriculumQueryService curriculum) {
		this.curriculum = curriculum;
	}

	@GetMapping("/curriculum")
	public CurriculumTree getCurriculum() {
		return curriculum.getPublishedTree();
	}

	@GetMapping("/lessons/{lessonId}")
	public LessonView getLesson(@PathVariable UUID lessonId) {
		return curriculum.getLesson(lessonId);
	}
}
