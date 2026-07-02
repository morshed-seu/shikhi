package com.shikhi.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A versioned snapshot of the whole curriculum tree. */
@Entity
@Table(name = "content_versions")
public class ContentVersion {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(nullable = false, unique = true)
	private String label;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ContentStatus status = ContentStatus.DRAFT;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column
	private String notes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ContentVersion() {
		// for JPA
	}

	public ContentVersion(String label, String notes) {
		this.label = label;
		this.notes = notes;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public void publish() {
		this.status = ContentStatus.PUBLISHED;
		this.publishedAt = Instant.now();
	}

	public void archive() {
		this.status = ContentStatus.ARCHIVED;
	}

	public UUID getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public ContentStatus getStatus() {
		return status;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public String getNotes() {
		return notes;
	}
}
