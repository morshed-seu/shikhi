package com.shikhi.identity.domain;

/** Account lifecycle state. {@link #DELETED} pairs with a soft-delete timestamp. */
public enum UserStatus {
	ACTIVE,
	SUSPENDED,
	DELETED
}
