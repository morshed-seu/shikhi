package com.shikhi.practice.plan;

import com.shikhi.practice.policy.NewCandidate;
import com.shikhi.practice.policy.ReviewCandidate;
import com.shikhi.practice.policy.WeakCandidate;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Fetches raw candidate lists for the three learning buckets (doc 42 §10: "SQL retrieves
 * candidate lists only, it never encodes ranking logic"). One native-SQL round trip per
 * bucket, in {@code PracticeWordPicker}'s style — no {@code CASE}/{@code ORDER BY} priority
 * math here; {@code com.shikhi.practice.policy} decides what to do with these lists.
 *
 * <p>Callers cap {@code limit} at a small multiple of the bucket's target (doc 42 §7.5: "a
 * bounded daily review budget... rather than ever surfacing an entire 500-item backlog") —
 * this repository does not decide that multiple, it only obeys the limit it's given.
 *
 * <p>A word already due for review is excluded from neither query here — {@code
 * DailyPlanService} de-dupes {@code weakWords()} results against {@code dueReviews()} results
 * before building plan items, since {@code UNIQUE(plan_id, vocabulary_id)} (V22) would
 * otherwise reject a word planned into two buckets at once. {@code newWords()} can never
 * collide: it only returns words with no {@code practice_word_progress} row, and a word can't
 * reach {@code review_progress} without first graduating through one.
 */
@Repository
public class PlanCandidateRepository {

	private final EntityManager em;

	public PlanCandidateRepository(EntityManager em) {
		this.em = em;
	}

	/** Due reviews, oldest {@code dueAt} (most overdue) first — final ranking is the policy's job. */
	@SuppressWarnings("unchecked")
	public List<ReviewCandidate> dueReviews(UUID userId, Instant now, int limit) {
		List<Object[]> rows = em.createNativeQuery("""
				select rp.vocabulary_id, rp.due_at, rp.failure_streak
				from review_progress rp
				where rp.user_id = :userId and rp.due_at <= :now
				order by rp.due_at asc
				limit :limit
				""")
				.setParameter("userId", userId)
				.setParameter("now", now)
				.setParameter("limit", limit)
				.getResultList();
		return rows.stream()
				.map(r -> new ReviewCandidate(uuid(r[0]), instant(r[1]), intVal(r[2])))
				.toList();
	}

	/**
	 * Words with low mastery or an active review failure streak, in the given CEFR bands.
	 * {@code masteryThreshold} is inclusive ({@code mastery_score <= threshold}).
	 */
	@SuppressWarnings("unchecked")
	public List<WeakCandidate> weakWords(UUID userId, Collection<String> bands,
			int masteryThreshold, int limit) {
		List<Object[]> rows = em.createNativeQuery("""
				select p.vocabulary_id, v.cefr_level, p.mastery_score,
				       coalesce(rp.failure_streak, 0), p.last_wrong_at
				from practice_word_progress p
				join vocabulary v on v.id = p.vocabulary_id
				left join review_progress rp
				  on rp.user_id = p.user_id and rp.vocabulary_id = p.vocabulary_id
				where p.user_id = :userId
				  and v.cefr_level in (:bands)
				  and (p.mastery_score <= :threshold or coalesce(rp.failure_streak, 0) > 0)
				order by p.mastery_score asc
				limit :limit
				""")
				.setParameter("userId", userId)
				.setParameter("bands", bands)
				.setParameter("threshold", masteryThreshold)
				.setParameter("limit", limit)
				.getResultList();
		return rows.stream()
				.map(r -> new WeakCandidate(uuid(r[0]), (String) r[1], intVal(r[2]), intVal(r[3]),
						instant(r[4])))
				.toList();
	}

	/** Words in the given CEFR bands never practiced by this learner, in random order. */
	@SuppressWarnings("unchecked")
	public List<NewCandidate> newWords(UUID userId, Collection<String> bands, int limit) {
		List<Object[]> rows = em.createNativeQuery("""
				select v.id, v.cefr_level
				from vocabulary v
				where v.cefr_level in (:bands)
				  and not exists (
				      select 1 from practice_word_progress p
				      where p.user_id = :userId and p.vocabulary_id = v.id
				  )
				order by random()
				limit :limit
				""")
				.setParameter("userId", userId)
				.setParameter("bands", bands)
				.setParameter("limit", limit)
				.getResultList();
		return rows.stream()
				.map(r -> new NewCandidate(uuid(r[0]), (String) r[1]))
				.toList();
	}

	// ---- defensive row-value coercion --------------------------------------------------------
	// Hibernate's native-query type inference for uuid/timestamptz columns is dialect/version
	// sensitive when no explicit result-set mapping is given; these helpers accept whichever
	// concrete JDBC type actually comes back rather than assuming one.

	private UUID uuid(Object value) {
		return value instanceof UUID u ? u : UUID.fromString(value.toString());
	}

	private Instant instant(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Instant i) {
			return i;
		}
		if (value instanceof OffsetDateTime odt) {
			return odt.toInstant();
		}
		if (value instanceof java.sql.Timestamp ts) {
			return ts.toInstant();
		}
		if (value instanceof java.time.LocalDateTime ldt) {
			return ldt.toInstant(ZoneOffset.UTC);
		}
		throw new IllegalStateException("Unsupported timestamp type: " + value.getClass());
	}

	private int intVal(Object value) {
		return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
	}
}
