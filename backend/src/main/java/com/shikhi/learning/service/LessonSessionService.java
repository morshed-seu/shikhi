package com.shikhi.learning.service;

import com.shikhi.content.domain.ContentStatus;
import com.shikhi.content.domain.ContentVersion;
import com.shikhi.content.repo.ContentVersionRepository;
import com.shikhi.content.service.CurriculumQueryService;
import com.shikhi.content.web.LessonView;
import com.shikhi.learning.domain.AnswerSubmission;
import com.shikhi.learning.domain.LessonSession;
import com.shikhi.learning.grading.GradingService;
import com.shikhi.learning.grading.GradingVerdict;
import com.shikhi.learning.repo.AnswerSubmissionRepository;
import com.shikhi.learning.repo.LessonSessionRepository;
import com.shikhi.learning.web.AnswerResult;
import com.shikhi.learning.web.LessonResult;
import com.shikhi.learning.web.LessonSessionResponse;
import com.shikhi.learning.web.Stats;
import com.shikhi.learning.web.SubmitAnswerRequest;
import com.shikhi.learning.web.Verdict;
import com.shikhi.platform.error.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a lesson play-through (LLD §4.1): start a session pinned to the published
 * content version, grade answers (delegating to the {@link GradingService} seam) while
 * tracking hearts, and finalize the score on completion. Answer submission and completion are
 * idempotent so client retries are safe (NFR-A4/DI1). Cross-session XP/streak/unlocking is M4.
 */
@Service
public class LessonSessionService {

	/** XP awarded per correct answer at completion (session-scoped in M3). */
	static final int XP_PER_CORRECT = 10;

	private final CurriculumQueryService curriculum;
	private final ContentVersionRepository versions;
	private final GradingService grading;
	private final LessonSessionRepository sessions;
	private final AnswerSubmissionRepository submissions;

	public LessonSessionService(CurriculumQueryService curriculum,
			ContentVersionRepository versions, GradingService grading,
			LessonSessionRepository sessions, AnswerSubmissionRepository submissions) {
		this.curriculum = curriculum;
		this.versions = versions;
		this.grading = grading;
		this.sessions = sessions;
		this.submissions = submissions;
	}

	@Transactional
	public LessonSessionResponse start(UUID userId, UUID lessonId) {
		// Validates the lesson exists in the published version (throws 404 otherwise).
		LessonView lesson = curriculum.getLesson(lessonId);
		ContentVersion published = versions.findFirstByStatus(ContentStatus.PUBLISHED)
				.orElseThrow(() -> ApiException.notFound("No published content"));

		LessonSession session = new LessonSession(userId, lessonId, published.getId());
		sessions.save(session);
		return LessonSessionResponse.of(session, lesson.contentVersion());
	}

	@Transactional
	public AnswerResult submitAnswer(UUID userId, UUID sessionId, SubmitAnswerRequest request) {
		LessonSession session = ownedSession(userId, sessionId);

		// Idempotent replay: return the original verdict without grading or charging a heart again.
		AnswerSubmission existing = submissions
				.findByUserIdAndIdempotencyKey(userId, request.idempotencyKey())
				.orElse(null);
		if (existing != null) {
			return new AnswerResult(Verdict.from(existing.toVerdict()),
					Stats.forHearts(session.getHeartsRemaining()));
		}

		if (session.isCompleted()) {
			throw ApiException.conflict("SESSION_COMPLETED", "This session is already completed");
		}

		GradingVerdict verdict = grading.grade(request.exerciseId(), request.answer());
		session.recordAnswer(verdict.correct());

		AnswerSubmission submission = new AnswerSubmission(sessionId, userId, request.exerciseId(),
				request.idempotencyKey(), verdict);
		try {
			submissions.saveAndFlush(submission);
		}
		catch (DataIntegrityViolationException race) {
			// Concurrent duplicate for the same key: roll back (no double heart charge).
			throw ApiException.conflict("DUPLICATE_SUBMISSION", "Answer already submitted");
		}
		return new AnswerResult(Verdict.from(verdict), Stats.forHearts(session.getHeartsRemaining()));
	}

	@Transactional
	public LessonResult complete(UUID userId, UUID sessionId) {
		LessonSession session = ownedSession(userId, sessionId);
		if (!session.isCompleted()) {
			session.complete();
		}
		return buildResult(session);
	}

	private LessonSession ownedSession(UUID userId, UUID sessionId) {
		LessonSession session = sessions.findById(sessionId)
				.orElseThrow(() -> ApiException.notFound("Session not found"));
		if (!session.getUserId().equals(userId)) {
			// Don't reveal another learner's session exists.
			throw ApiException.notFound("Session not found");
		}
		return session;
	}

	private LessonResult buildResult(LessonSession session) {
		int xpEarned = session.getScore() * XP_PER_CORRECT;
		return new LessonResult(session.getScore(), xpEarned, List.of(), 0,
				Stats.forHearts(session.getHeartsRemaining()));
	}
}
