package com.shikhi.identity.domain;

/** Sign-in method (D5). Email ships in M1; phone/Google are post-pilot behind the same seam. */
public enum Provider {
	EMAIL,
	PHONE,
	GOOGLE
}
