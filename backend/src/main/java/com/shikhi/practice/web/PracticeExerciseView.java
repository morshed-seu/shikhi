package com.shikhi.practice.web;

import com.shikhi.content.web.Bilingual;
import com.shikhi.practice.domain.PracticeExercise;
import com.shikhi.practice.domain.PracticeExerciseType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contract {@code PracticeExercise} — what the learner sees. {@code config} is built
 * strictly from the entity's learner-visible payload and must never contain the answer
 * key. {@code solution} is the entity's server-only answer key, intentionally exposed here
 * so the web client can grade practice answers locally and submit a batch (E12 batch
 * submit) — {@code config} still carries no correctness signal.
 */
public record PracticeExerciseView(UUID id, PracticeExerciseType type, int ordinal,
		Bilingual prompt, Map<String, Object> config, PracticeSolutionView solution) {

	public static PracticeExerciseView from(PracticeExercise exercise) {
		return new PracticeExerciseView(exercise.getId(), exercise.getType(),
				exercise.getOrdinal(),
				new Bilingual(exercise.getPromptEn(), exercise.getPromptBn()),
				exercise.getPayload(),
				PracticeSolutionView.from(exercise.getAnswerKey()));
	}

	/**
	 * The server-only answer key, mirrored from {@link PracticeExercise#getAnswerKey()}:
	 * MCQ types (WORD_MEANING/MEANING_WORD/SENTENCE_GAP) populate {@code correctOptionId};
	 * SENTENCE_BUILD/TYPE_WORD populate {@code accepted}. {@code reveal} is always present.
	 */
	public record PracticeSolutionView(String correctOptionId, List<String> accepted,
			String reveal) {

		static PracticeSolutionView from(Map<String, Object> answerKey) {
			Object correctOptionId = answerKey.get("correctOptionId");
			Object accepted = answerKey.get("accepted");
			Object reveal = answerKey.get("revealText");
			return new PracticeSolutionView(
					correctOptionId == null ? null : correctOptionId.toString(),
					accepted instanceof List<?> list && !list.isEmpty()
							? list.stream().map(Object::toString).toList()
							: null,
					reveal == null ? null : reveal.toString());
		}
	}
}
