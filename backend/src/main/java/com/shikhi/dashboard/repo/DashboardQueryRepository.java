package com.shikhi.dashboard.repo;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The LLD §1 sanctioned read-only reporting seam: {@code dashboard} owns this query
 * repository and runs SELECT-only native SQL aggregating across other modules' tables
 * (lesson answers, practice answers/sessions, lesson completions) — a CQRS-style read model
 * for lifetime totals. Rationale (LLD §2.9): threading one-line COUNT methods through three
 * owning modules adds coupling without adding safety. Constraints: SELECT-only, scalar
 * projections only (no entity mappings from other modules).
 *
 * <p>Tables touched (all pre-existing; no new table is introduced by this seam): {@code
 * answer_submissions}, {@code practice_answers}, {@code user_progress}, {@code
 * practice_sessions}.
 */
@Repository
public class DashboardQueryRepository {

	private final JdbcTemplate jdbc;

	public DashboardQueryRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Lifetime graded-answer totals across lessons + practice (contract {@code totalAnswered}
	 * / {@code totalCorrect}) — a UNION ALL over {@code answer_submissions} and {@code
	 * practice_answers}, aggregated in one round trip.
	 */
	public LifetimeTotals lifetimeTotals(UUID userId) {
		LifetimeTotals totals = jdbc.queryForObject("""
				select count(*) as answered, count(*) filter (where correct) as correct
				from (
					select correct from answer_submissions where user_id = ?
					union all
					select correct from practice_answers where user_id = ?
				) all_answers
				""", (rs, rowNum) -> new LifetimeTotals(rs.getLong("answered"), rs.getLong("correct")),
				userId, userId);
		return totals == null ? new LifetimeTotals(0, 0) : totals;
	}

	/** Lessons completed (contract {@code lessonsCompleted}) — {@code user_progress} rows. */
	public long lessonsCompleted(UUID userId) {
		Long result = jdbc.queryForObject(
				"select count(*) from user_progress where user_id = ? and status = 'COMPLETED'",
				Long.class, userId);
		return result == null ? 0 : result;
	}

	/**
	 * Practice sessions completed (contract {@code practiceSessionsCompleted}) — {@code
	 * practice_sessions} rows with a non-null {@code completed_at}.
	 */
	public long practiceSessionsCompleted(UUID userId) {
		Long result = jdbc.queryForObject("""
				select count(*) from practice_sessions
				where user_id = ? and completed_at is not null
				""", Long.class, userId);
		return result == null ? 0 : result;
	}

	/** Lifetime answered/correct pair, scalar-projected (no entity mapping). */
	public record LifetimeTotals(long answered, long correct) {
	}
}
