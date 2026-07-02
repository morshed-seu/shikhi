package com.shikhi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A lesson within a unit; holds an ordered set of exercises. */
@Entity
@Table(name = "lessons")
public class Lesson {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "unit_id", nullable = false)
	private UUID unitId;

	@Column(nullable = false)
	private String code;

	@Column(name = "title_en", nullable = false)
	private String titleEn;

	@Column(name = "title_bn", nullable = false)
	private String titleBn;

	@Column(nullable = false)
	private int ordinal;

	protected Lesson() {
		// for JPA
	}

	public Lesson(UUID unitId, String code, String titleEn, String titleBn, int ordinal) {
		this.unitId = unitId;
		this.code = code;
		this.titleEn = titleEn;
		this.titleBn = titleBn;
		this.ordinal = ordinal;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUnitId() {
		return unitId;
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
