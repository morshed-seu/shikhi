package com.shikhi.content.web;

import com.shikhi.content.repo.VocabularyRepository;
import com.shikhi.platform.error.ApiException;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Learner-facing vocabulary (dictionary) browser, one CEFR band at a time. */
@RestController
@RequestMapping("/v1/vocabulary")
public class VocabularyController {

	private static final Set<String> LEVELS = Set.of("A1", "A2", "B1", "B2", "C1");

	private final VocabularyRepository vocabulary;

	public VocabularyController(VocabularyRepository vocabulary) {
		this.vocabulary = vocabulary;
	}

	/** Words for one CEFR level (default A1), in alphabetical order. */
	@GetMapping
	public List<VocabularyEntry> list(@RequestParam(defaultValue = "A1") String level) {
		String normalized = level.toUpperCase();
		if (!LEVELS.contains(normalized)) {
			throw ApiException.badRequest("INVALID_LEVEL", "level must be one of A1, A2, B1, B2, C1");
		}
		return vocabulary.findByCefrLevelOrderByOrdinal(normalized).stream()
				.map(VocabularyEntry::from)
				.toList();
	}
}
