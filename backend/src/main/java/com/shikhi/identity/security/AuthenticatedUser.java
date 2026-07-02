package com.shikhi.identity.security;

import java.util.List;
import java.util.UUID;

/** Authenticated principal carried in the security context (derived from the access JWT). */
public record AuthenticatedUser(UUID id, List<String> roles) {
}
