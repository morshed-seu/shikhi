package com.shikhi.identity.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bound from {@code shikhi.security.jwt.*}. */
@ConfigurationProperties(prefix = "shikhi.security.jwt")
public class JwtProperties {

	/** HMAC signing secret (>= 32 bytes for HS256). Injected from a secret manager/env. */
	private String secret;

	/** Expected/issued {@code iss} claim. */
	private String issuer = "shikhi";

	/** Access-token lifetime (e.g. PT15M). */
	private Duration accessTtl = Duration.ofMinutes(15);

	/** Refresh-token lifetime (e.g. P30D). */
	private Duration refreshTtl = Duration.ofDays(30);

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}

	public Duration getAccessTtl() {
		return accessTtl;
	}

	public void setAccessTtl(Duration accessTtl) {
		this.accessTtl = accessTtl;
	}

	public Duration getRefreshTtl() {
		return refreshTtl;
	}

	public void setRefreshTtl(Duration refreshTtl) {
		this.refreshTtl = refreshTtl;
	}
}
