package com.shikhi.learning.web;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Contract {@code StartSessionRequest}. */
public record StartSessionRequest(@NotNull UUID lessonId) {
}
