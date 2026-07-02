package com.shikhi.review.web;

import com.shikhi.content.web.Bilingual;
import java.time.Instant;
import java.util.UUID;

/** Contract {@code ReviewItem} — a due review exercise (self-graded recall in M6). */
public record ReviewItemView(UUID exerciseId, Bilingual prompt, int boxLevel, Instant dueAt) {
}
