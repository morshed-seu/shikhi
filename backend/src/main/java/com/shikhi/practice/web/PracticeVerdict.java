package com.shikhi.practice.web;

import com.shikhi.content.web.Bilingual;

/**
 * Contract {@code Verdict} for practice answers. A wrong answer's feedback reveals the
 * correct answer (the moment of learning); correctness flags never appear anywhere else.
 */
public record PracticeVerdict(boolean correct, Bilingual feedback) {
}
