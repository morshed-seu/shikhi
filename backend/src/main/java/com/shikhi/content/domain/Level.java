package com.shikhi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A CEFR-style level (e.g. A1) within a content version. */
@Entity
@Table(name = "levels")
public class Level {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "content_version_id", nullable = false)
	private UUID contentVersionId;

	@Column(nullable = false)
	private String code;

	@Column(name = "title_en", nullable = false)
	private String titleEn;

	@Column(name = "title_bn", nullable = false)
	private String titleBn;

	@Column(nullable = false)
	private int ordinal;

	protected Level() {
		// for JPA
	}

	public Level(UUID contentVersionId, String code, String titleEn, String titleBn, int ordinal) {
		this.contentVersionId = contentVersionId;
		this.code = code;
		this.titleEn = titleEn;
		this.titleBn = titleBn;
		this.ordinal = ordinal;
	}

	public UUID getId() {
		return id;
	}

	public UUID getContentVersionId() {
		return contentVersionId;
	}

	public String getCode() {
		return code;
	}

	public String getTitleEn() {
		return titleEn;
	}

	public String getTitleBn() {
		return titleBn;
	}

	public int getOrdinal() {
		return ordinal;
	}
}
