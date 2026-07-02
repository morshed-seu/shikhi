package com.shikhi.content;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caching for published content reads (F4/J5). An in-process cache is fine for a single
 * instance; ADR-0004 swaps in Redis when we scale to multiple instances so publish can
 * invalidate across the fleet. Publishing evicts both caches (see AuthoringService).
 */
@Configuration
@EnableCaching
public class CacheConfig {

	public static final String CURRICULUM_TREE = "curriculumTree";
	public static final String LESSONS = "lessons";

	@Bean
	public CacheManager cacheManager() {
		return new ConcurrentMapCacheManager(CURRICULUM_TREE, LESSONS);
	}
}
