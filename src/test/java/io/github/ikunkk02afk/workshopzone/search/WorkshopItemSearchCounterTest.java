package io.github.ikunkk02afk.workshopzone.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopItemSearchCounterTest {
	@Test
	void countsMatchingItemAcrossAllSlotsUsingLong() {
		WorkshopItemSearchCounter.Count result = WorkshopItemSearchCounter.countSamples(List.of(
			new WorkshopItemSearchCounter.StackSample("iron", 64, "plain"),
			new WorkshopItemSearchCounter.StackSample("iron", 32, "plain"),
			new WorkshopItemSearchCounter.StackSample("gold", 16, "plain")
		), "iron");

		assertEquals(96L, result.itemCount());
		assertEquals(2, result.matchingSlotCount());
		assertFalse(result.multipleVariants());
	}

	@Test
	void sameItemWithDifferentComponentsCountsTogetherAndReportsVariants() {
		WorkshopItemSearchCounter.Count result = WorkshopItemSearchCounter.countSamples(List.of(
			new WorkshopItemSearchCounter.StackSample("potion", 2, "water"),
			new WorkshopItemSearchCounter.StackSample("potion", 3, "healing")
		), "potion");

		assertEquals(5L, result.itemCount());
		assertEquals(2, result.matchingSlotCount());
		assertTrue(result.multipleVariants());
	}
}
