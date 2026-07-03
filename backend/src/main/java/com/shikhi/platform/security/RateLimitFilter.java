package com.shikhi.platform.security;

import com.shikhi.platform.web.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client rate limiting for authentication endpoints (NFR-SEC5, threat model §S "credential
 * stuffing / login brute force"). Runs just after the correlation-id filter and before the
 * security chain, so throttled requests never reach auth logic. Fails open: any internal error
 * lets the request through rather than locking users out.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

	private final RateLimitProperties props;
	private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

	public RateLimitFilter(RateLimitProperties props) {
		this.props = props;
	}

	/** Guard only the sensitive, unauthenticated auth endpoints. */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!props.isEnabled()) {
			return true;
		}
		String path = request.getServletPath();
		return path == null || !path.startsWith("/v1/auth/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		boolean allowed;
		try {
			allowed = bucketFor(clientKey(request)).tryConsume();
		} catch (RuntimeException ex) {
			// Fail open — a limiter bug must never take down login.
			log.warn("Rate limiter error; allowing request [correlationId={}]",
					CorrelationId.current(), ex);
			allowed = true;
		}

		if (allowed) {
			filterChain.doFilter(request, response);
			return;
		}
		reject(response);
	}

	private TokenBucket bucketFor(String key) {
		// Crude bound on memory: if the map explodes (e.g. IP spoofing), reset it wholesale.
		if (buckets.size() > props.getMaxTrackedClients()) {
			buckets.clear();
		}
		return buckets.computeIfAbsent(key, k -> new TokenBucket(props.getCapacity(),
				props.getRefillTokens(),
				TimeUnit.SECONDS.toNanos(props.getRefillPeriodSeconds())));
	}

	/** Client identity for the bucket: first hop of X-Forwarded-For (behind the LB) or peer IP. */
	private String clientKey(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private void reject(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(props.getRefillPeriodSeconds()));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		// Match the contract Error shape without pulling in an ObjectMapper.
		String body = "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests — please wait "
				+ "and try again.\",\"correlationId\":\"" + CorrelationId.current() + "\"}";
		response.getWriter().write(body);
	}
}
