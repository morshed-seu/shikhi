package com.shikhi.practice.service;

import com.shikhi.content.domain.Vocabulary;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Selects vocabulary for practice rounds (E12). One SQL round-trip joins the band's words
 * with the learner's per-word strength and orders weakest-first: missed words (strength
 * 0–1) lead, unseen words (no row → COALESCE {@code 2}) follow, mastered words (3–5) trail;
 * ties are randomized so rounds stay varied. Reads the content module's vocabulary table
 * (read-only, per the LLD dependency rules).
 */
@Component
public class PracticeWordPicker {

	private final EntityManager em;

	public PracticeWordPicker(EntityManager em) {
		this.em = em;
	}

	/** Weakest-first pick of {@code limit} words from {@code bands}, excluding {@code usedIds}. */
	@SuppressWarnings("unchecked")
	public List<Vocabulary> pick(UUID userId, Collection<String> bands, Collection<UUID> usedIds,
			int limit) {
		String exclusion = usedIds.isEmpty() ? "" : "and v.id not in (:usedIds)";
		var query = em.createNativeQuery("""
				select v.* from vocabulary v
				left join practice_word_progress p
				  on p.vocabulary_id = v.id and p.user_id = :userId
				where v.cefr_level in (:bands) %s
				order by coalesce(p.strength, 2), random()
				limit :limit
				""".formatted(exclusion), Vocabulary.class)
				.setParameter("userId", userId)
				.setParameter("bands", bands)
				.setParameter("limit", limit);
		if (!usedIds.isEmpty()) {
			query.setParameter("usedIds", usedIds);
		}
		return query.getResultList();
	}

	/** Random same-band words to serve as MCQ distractors (picked fresh per round). */
	@SuppressWarnings("unchecked")
	public List<Vocabulary> distractorPool(String band, int limit) {
		return em.createNativeQuery("""
				select v.* from vocabulary v
				where v.cefr_level = :band
				order by random()
				limit :limit
				""", Vocabulary.class)
				.setParameter("band", band)
				.setParameter("limit", limit)
				.getResultList();
	}
}
