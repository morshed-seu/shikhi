package com.shikhi.identity.security;

import com.shikhi.platform.web.CorrelationId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Returns the contract's {@code Error} JSON (401) instead of a redirect for API clients.
 * The body is a fixed shape with a machine-safe correlation id (a UUID), so it is written
 * directly rather than via a message converter — keeping the security layer free of a
 * Jackson bean dependency.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		String body = "{\"code\":\"UNAUTHORIZED\","
				+ "\"message\":\"Authentication required\","
				+ "\"correlationId\":\"" + CorrelationId.current() + "\"}";
		response.getWriter().write(body);
	}
}
