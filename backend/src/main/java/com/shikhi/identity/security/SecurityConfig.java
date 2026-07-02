package com.shikhi.identity.security;

import java.util.HashMap;
import java.util.Map;
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

/** Stateless, token-based security (ADR-0005). */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

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
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/v1/auth/**", "/v1/health", "/v1/ready").permitAll()
						.requestMatchers("/actuator/**").permitAll()
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
