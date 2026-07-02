package com.shikhi.identity.service;

/** Access + refresh token pair returned by register/login/refresh (contract {@code TokenPair}). */
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
}
