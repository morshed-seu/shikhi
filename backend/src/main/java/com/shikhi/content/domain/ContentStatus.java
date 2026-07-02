package com.shikhi.content.domain;

/** Lifecycle of a content version. PUBLISHED is immutable; publishing archives the prior one. */
public enum ContentStatus {
	DRAFT,
	PUBLISHED,
	ARCHIVED
}
