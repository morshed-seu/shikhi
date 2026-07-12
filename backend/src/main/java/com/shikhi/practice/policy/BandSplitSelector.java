package com.shikhi.practice.policy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Current/earlier-band split-with-backfill mechanics shared by {@link NewWordSelectionPolicy}
 * and {@link WeakSelectionPolicy} (doc 42 §6.5): {@code currentBandPercent} of {@code target}
 * from {@code currentPool}, the rest from {@code earlierPool}, then any shortfall backfilled
 * from {@code fallbackPool} — whichever band-agnostic ordering the caller wants for the
 * shortfall case (priority order for WEAK, pre-shuffled SQL order for NEW). Package-private:
 * an implementation detail the two policies share, not part of either's public API — each
 * policy still owns its own filtering/sorting and keeps its own public {@code select} method.
 */
final class BandSplitSelector {

	private BandSplitSelector() {
	}

	static <T> List<T> select(List<T> currentPool, List<T> earlierPool, List<T> fallbackPool,
			int target, int currentBandPercent, Function<T, UUID> idOf) {
		int currentTarget = Math.round(target * currentBandPercent / 100f);
		int earlierTarget = target - currentTarget;

		List<T> selected = new ArrayList<>();
		Set<UUID> selectedIds = new HashSet<>();
		take(currentPool, currentTarget, selected, selectedIds, idOf);
		take(earlierPool, earlierTarget, selected, selectedIds, idOf);

		// Shortfall on one side: fill from whatever remains, regardless of band (doc 42 §6.5 —
		// the split is a goal, never a reason to leave slots empty).
		int remaining = target - selected.size();
		if (remaining > 0) {
			take(fallbackPool, remaining, selected, selectedIds, idOf);
		}
		return selected;
	}

	private static <T> void take(List<T> pool, int count, List<T> into, Set<UUID> seen,
			Function<T, UUID> idOf) {
		int taken = 0;
		for (T c : pool) {
			if (taken >= count) {
				break;
			}
			if (seen.add(idOf.apply(c))) {
				into.add(c);
				taken++;
			}
		}
	}
}
