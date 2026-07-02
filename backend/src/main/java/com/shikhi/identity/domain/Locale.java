package com.shikhi.identity.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * UI language for a learner. Serialized as the lowercase code ("bn"/"en") in both JSON
 * (API contract {@code Locale} schema) and the database ({@code users.ui_locale}).
 */
public enum Locale {
	BN("bn"),
	EN("en");

	private final String code;

	Locale(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}

	@JsonCreator
	public static Locale fromCode(String value) {
		for (Locale l : values()) {
			if (l.code.equalsIgnoreCase(value)) {
				return l;
			}
		}
		throw new IllegalArgumentException("Unknown locale: " + value);
	}
}
