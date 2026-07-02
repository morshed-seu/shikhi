package com.shikhi.identity.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Persists {@link Locale} as its lowercase code, matching the {@code ui_locale} check. */
@Converter(autoApply = true)
public class LocaleConverter implements AttributeConverter<Locale, String> {

	@Override
	public String convertToDatabaseColumn(Locale attribute) {
		return attribute == null ? null : attribute.code();
	}

	@Override
	public Locale convertToEntityAttribute(String dbData) {
		return dbData == null ? null : Locale.fromCode(dbData);
	}
}
