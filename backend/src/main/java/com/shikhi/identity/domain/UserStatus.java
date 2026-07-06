package com.shikhi.identity.domain;

/**
 * Account lifecycle state. {@link #DELETED} pairs with a soft-delete timestamp.
 * {@link #ANONYMOUS} is a guest learner with no linked identity/credential yet — it becomes
 * {@link #ACTIVE} in place when the guest claims the account by adding email+password.
 */
public enum UserStatus {
	ACTIVE,
	SUSPENDED,
	DELETED,
	ANONYMOUS
}
