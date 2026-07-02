package com.shikhi.review.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit tests for Leitner box transitions (LLD §2.6). No database. */
class ReviewItemTest {

	private ReviewItem newItem() {
		return new ReviewItem(UUID.randomUUID(), UUID.randomUUID());
	}

	@Test
	void aMissedItemStartsInBoxOneDueImmediately() {
		ReviewItem item = newItem();
		assertThat(item.getBoxLevel()).isEqualTo(1);
		assertThat(item.getDueAt()).isBeforeOrEqualTo(Instant.now());
	}

	@Test
	void recallPromotesTheBoxAndSchedulesFurtherOut() {
		ReviewItem item = newItem();
		item.recalled();
		assertThat(item.getBoxLevel()).isEqualTo(2);
		assertThat(item.getDueAt()).isAfter(Instant.now());
	}

	@Test
	void recallsCapAtTheMaximumBox() {
		ReviewItem item = newItem();
		for (int i = 0; i < 10; i++) {
			item.recalled();
		}
		assertThat(item.getBoxLevel()).isEqualTo(ReviewScheduler.MAX_BOX);
	}

	@Test
	void missingAgainResetsToBoxOneDueImmediately() {
		ReviewItem item = newItem();
		item.recalled();
		item.recalled();
		item.missed();
		assertThat(item.getBoxLevel()).isEqualTo(1);
		assertThat(item.getDueAt()).isBeforeOrEqualTo(Instant.now());
	}
}
