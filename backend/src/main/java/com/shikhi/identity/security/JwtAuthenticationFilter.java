package com.shikhi.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from a {@code Authorization: Bearer <jwt>} header. Invalid or
 * missing tokens leave the context unauthenticated; the entry point then returns 401 for
 * protected routes (public routes still proceed).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER = "Bearer ";

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.startsWith(BEARER)
				&& SecurityContextHolder.getContext().getAuthentication() == null) {
			String token = header.substring(BEARER.length()).trim();
			try {
				AuthenticatedUser principal = jwtService.parse(token);
				var authorities = principal.roles().stream()
						.map(r -> new SimpleGrantedAuthority("ROLE_" + r))
						.toList();
				var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
				SecurityContextHolder.getContext().setAuthentication(auth);
			} catch (JwtException | IllegalArgumentException ex) {
				// Invalid token → stay anonymous; do not leak why here.
				SecurityContextHolder.clearContext();
			}
		}
		filterChain.doFilter(request, response);
	}
}
