package com.shikhi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A unit within a level. */
@Entity
@Table(name = "units")
public class Unit {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "level_id", nullable = false)
	private UUID levelId;

	@Column(nullable = false)
	private String code;

	@Column(name = "title_en", nullable = false)
	private String titleEn;

	@Column(name = "title_bn", nullable = false)
	private String titleBn;

	@Column(nullable = false)
	private int ordinal;

	protected Unit() {
		// for JPA
	}

	public Unit(UUID levelId, String code, String titleEn, String titleBn, int ordinal) {
		this.levelId = levelId;
		this.code = code;
		this.titleEn = titleEn;
		this.titleBn = titleBn;
		this.ordinal = ordinal;
	}

	public UUID getId() {
		return id;
	}

	public UUID getLevelId() {
		return levelId;
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
