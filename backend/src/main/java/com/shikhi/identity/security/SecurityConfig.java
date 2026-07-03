package com.shikhi.identity.security;

import com.shikhi.platform.security.RateLimitProperties;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Stateless, token-based security (ADR-0005). */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class})
public class SecurityConfig {

	/**
	 * Cross-origin allowlist for the browser SPA. Empty (default) means same-origin only, which
	 * is correct when the SPA is served behind the same gateway. Set
	 * {@code shikhi.security.cors.allowed-origins} (comma-separated) per environment.
	 */
	@Value("${shikhi.security.cors.allowed-origins:}")
	private String corsAllowedOrigins;

	/** Encoding id we store new hashes with (also recorded in {@code credentials.algo}). */
	public static final String ENCODE_ID = "argon2";

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final RestAuthenticationEntryPoint authenticationEntryPoint;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			RestAuthenticationEntryPoint authenticationEntryPoint) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable()) // stateless bearer-token API; no cookies/sessions
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(headers -> headers
						// Defence-in-depth response headers (threat model §Web; NFR-SEC1/SEC4).
						.contentTypeOptions(opts -> {}) // X-Content-Type-Options: nosniff
						.frameOptions(frame -> frame.deny()) // clickjacking: X-Frame-Options: DENY
						.httpStrictTransportSecurity(hsts -> hsts // HSTS (NFR-SEC1)
								.includeSubDomains(true)
								.maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
						.referrerPolicy(ref -> ref.policy(
								org.springframework.security.web.header.writers
										.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
						// API returns JSON only; lock the CSP right down (no scripts/frames).
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'none'; frame-ancestors 'none'"))
						.permissionsPolicyHeader(pp -> pp.policy(
								"geolocation=(), microphone=(), camera=()")))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/v1/auth/**", "/v1/health", "/v1/ready").permitAll()
						// Liveness/readiness must be probe-reachable; other actuator endpoints
						// (metrics, info) are ADMIN-only so they aren't publicly scrapable.
						.requestMatchers("/actuator/health/**").permitAll()
						.requestMatchers("/actuator/**").hasRole("ADMIN")
						// The container ERROR-dispatches to /error; it must be reachable so a
						// 403/500 isn't re-evaluated as anonymous and turned into a 401.
						.requestMatchers("/error").permitAll()
						// Authoring is role-gated (AUTHOR/ADMIN); learners get 403.
						.requestMatchers("/v1/admin/**").hasAnyRole("AUTHOR", "ADMIN")
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
				.addFilterBefore(jwtAuthenticationFilter,
						UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * Stop Boot from also registering the JWT filter as a top-level servlet filter — it must
	 * run ONLY inside the Spring Security chain. Otherwise it executes twice per request
	 * (once outside the chain), which corrupts authorization decisions (e.g. an authenticated
	 * but under-privileged user getting 401 instead of 403).
	 */
	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(
			JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration =
				new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	/**
	 * CORS policy for the browser SPA. Only the configured origins may make cross-origin calls
	 * with the {@code Authorization} header; empty allowlist ⇒ same-origin only (no CORS).
	 */
	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		String trimmed = corsAllowedOrigins == null ? "" : corsAllowedOrigins.trim();
		if (!trimmed.isEmpty()) {
			CorsConfiguration config = new CorsConfiguration();
			config.setAllowedOrigins(Arrays.stream(trimmed.split(","))
					.map(String::trim).filter(s -> !s.isEmpty()).toList());
			config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
			config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
			config.setExposedHeaders(List.of("X-Correlation-Id"));
			config.setAllowCredentials(false); // bearer token in header, not cookies
			config.setMaxAge(Duration.ofHours(1));
			source.registerCorsConfiguration("/**", config);
		}
		return source;
	}

	/**
	 * Argon2id for new hashes, with bcrypt understood for verification so credentials can
	 * be migrated without forcing a reset. The {@code {id}} prefix is stored in the hash.
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		Map<String, PasswordEncoder> encoders = new HashMap<>();
		encoders.put(ENCODE_ID, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8());
		encoders.put("bcrypt", new BCryptPasswordEncoder());
		return new DelegatingPasswordEncoder(ENCODE_ID, encoders);
	}
}
