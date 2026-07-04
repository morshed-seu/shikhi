package com.shikhi.progress.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Contract {@code SetLevelRequest} — self-placement / accepted level-up (E12). */
public record SetLevelRequest(
		@NotBlank @Pattern(regexp = "A1|A2|B1|B2", message = "must be one of A1, A2, B1, B2")
		String cefrLevel) {
}
