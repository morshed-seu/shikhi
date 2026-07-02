package com.shikhi.learning.web;

import com.shikhi.learning.domain.LessonSession;
import java.util.UUID;

/** Contract {@code LessonSession}. */
public record LessonSessionResponse(UUID id, UUID lessonId, String contentVersion,
		int heartsRemaining, String status) {

	public static LessonSessionResponse of(LessonSession session, String contentVersion) {
		return new LessonSessionResponse(session.getId(), session.getLessonId(), contentVersion,
				session.getHeartsRemaining(), session.getStatus().name());
	}
}
