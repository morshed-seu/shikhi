package com.shikhi.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for the auth rate limiter. It runs as a top-level servlet filter (MockMvc bypasses
 * those), so it is exercised directly against mock request/response objects.
 */
class RateLimitFilterTest {

	private RateLimitProperties props(int capacity, int refill, long periodSeconds) {
		RateLimitProperties p = new RateLimitProperties();
		p.setEnabled(true);
		p.setCapacity(capacity);
		p.setRefillTokens(refill);
		p.setRefillPeriodSeconds(periodSeconds);
		return p;
	}

	private MockHttpServletRequest authRequest(String ip) {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/v1/auth/login");
		req.setServletPath("/v1/auth/login");
		req.setRemoteAddr(ip);
		return req;
	}

	@Test
	void allowsUpToCapacityThenReturns429() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(props(3, 3, 60));
		int[] chainCalls = {0};
		FilterChain chain = (r, s) -> chainCalls[0]++;

		for (int i = 0; i < 3; i++) {
			MockHttpServletResponse res = new MockHttpServletResponse();
			filter.doFilter(authRequest("1.1.1.1"), res, chain);
			assertThat(res.getStatus()).isEqualTo(200);
		}
		// 4th within the window is throttled.
		MockHttpServletResponse throttled = new MockHttpServletResponse();
		filter.doFilter(authRequest("1.1.1.1"), throttled, chain);

		assertThat(chainCalls[0]).isEqualTo(3);
		assertThat(throttled.getStatus()).isEqualTo(429);
		assertThat(throttled.getHeader("Retry-After")).isEqualTo("60");
		assertThat(throttled.getContentAsString()).contains("RATE_LIMITED");
	}

	@Test
	void limitsArePerClientKey() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(props(1, 1, 60));
		FilterChain chain = (r, s) -> {};

		MockHttpServletResponse a = new MockHttpServletResponse();
		filter.doFilter(authRequest("2.2.2.2"), a, chain);
		assertThat(a.getStatus()).isEqualTo(200);

		// A different client still has its own full bucket.
		MockHttpServletResponse b = new MockHttpServletResponse();
		filter.doFilter(authRequest("3.3.3.3"), b, chain);
		assertThat(b.getStatus()).isEqualTo(200);

		// The first client is now empty.
		MockHttpServletResponse aAgain = new MockHttpServletResponse();
		filter.doFilter(authRequest("2.2.2.2"), aAgain, chain);
		assertThat(aAgain.getStatus()).isEqualTo(429);
	}

	@Test
	void nonAuthAndDisabledRequestsAreNotFiltered() throws Exception {
		RateLimitFilter filter = new RateLimitFilter(props(1, 1, 60));
		MockHttpServletRequest curriculum = new MockHttpServletRequest("GET", "/v1/curriculum");
		curriculum.setServletPath("/v1/curriculum");
		assertThat(filter.shouldNotFilter(curriculum)).isTrue();

		RateLimitProperties disabled = props(1, 1, 60);
		disabled.setEnabled(false);
		assertThat(new RateLimitFilter(disabled).shouldNotFilter(authRequest("4.4.4.4"))).isTrue();
	}

	@Test
	void tokenBucketRefillsOverTime() {
		TokenBucket bucket = new TokenBucket(1, 1, TimeUnit.MILLISECONDS.toNanos(1));
		assertThat(bucket.tryConsume()).isTrue();
		assertThat(bucket.tryConsume()).isFalse();
		// After the refill period a token is available again.
		try {
			Thread.sleep(5);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		assertThat(bucket.tryConsume()).isTrue();
	}
}
