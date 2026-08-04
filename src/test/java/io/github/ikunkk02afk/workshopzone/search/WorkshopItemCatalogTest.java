package io.github.ikunkk02afk.workshopzone.search;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopItemCatalogTest {
	@Test
	void builderContainsOnlyActualItemsAndCountsEachLogicalContainerOnce() {
		WorkshopItemCatalog catalog = WorkshopItemCatalogBuilder.aggregateSamples(List.of(
			List.of(
				new WorkshopItemCatalogBuilder.StackSample(Identifier.ofVanilla("iron_ingot"), 32, "plain"),
				new WorkshopItemCatalogBuilder.StackSample(Identifier.ofVanilla("iron_ingot"), 16, "plain"),
				new WorkshopItemCatalogBuilder.StackSample(Identifier.ofVanilla("gold_ingot"), 8, "plain")
			),
			List.of(new WorkshopItemCatalogBuilder.StackSample(Identifier.ofVanilla("iron_ingot"), 64, "plain"))
		), itemId -> true, WorkshopItemCatalog.MAX_CATALOG_ENTRIES);

		assertEquals(List.of(Identifier.ofVanilla("iron_ingot"), Identifier.ofVanilla("gold_ingot")),
			catalog.entries().stream().map(WorkshopItemCatalogEntry::itemId).toList());
		WorkshopItemCatalogEntry iron = catalog.entries().getFirst();
		assertEquals(112L, iron.totalCount());
		assertEquals(2, iron.matchingContainerCount());
		assertFalse(iron.multipleVariants());
		assertTrue(catalog.entries().stream().noneMatch(entry -> entry.itemId().equals(Identifier.ofVanilla("iron_chestplate"))));
		assertTrue(catalog.entries().stream().noneMatch(entry -> entry.itemId().equals(Identifier.ofVanilla("air"))));
	}

	@Test
	void deniedItemsAreExcludedWithoutAffectingAllowedItems() {
		WorkshopItemCatalog catalog = WorkshopItemCatalogBuilder.aggregateSamples(List.of(List.of(
			new WorkshopItemCatalogBuilder.StackSample(Identifier.ofVanilla("iron_ingot"), 4, "plain"),
			new WorkshopItemCatalogBuilder.StackSample(Identifier.ofVanilla("diamond"), 2, "plain")
		)), itemId -> !itemId.equals(Identifier.ofVanilla("diamond")), WorkshopItemCatalog.MAX_CATALOG_ENTRIES);

		assertEquals(List.of(Identifier.ofVanilla("iron_ingot")),
			catalog.entries().stream()
				.map(WorkshopItemCatalogEntry::itemId).toList());
	}

	@Test
	void sameItemVariantsMergeAndSetMultipleVariantsAcrossContainers() {
		WorkshopItemCatalogEntry potion = WorkshopItemCatalogBuilder.aggregateSamples(List.of(
			List.of(new WorkshopItemCatalogBuilder.StackSample(Identifier.ofVanilla("potion"), 1, "water")),
			List.of(new WorkshopItemCatalogBuilder.StackSample(Identifier.ofVanilla("potion"), 1, "healing"))
		), itemId -> true, WorkshopItemCatalog.MAX_CATALOG_ENTRIES).entries().getFirst();

		assertEquals(Identifier.ofVanilla("potion"), potion.itemId());
		assertEquals(2L, potion.totalCount());
		assertEquals(2, potion.matchingContainerCount());
		assertTrue(potion.multipleVariants());
	}

	@Test
	void catalogSortsDeterministicallyAndTruncatesAfterSorting() {
		WorkshopItemCatalog catalog = WorkshopItemCatalog.fromEntries(List.of(
			new WorkshopItemCatalogEntry(Identifier.ofVanilla("copper_ingot"), 10, 1, false),
			new WorkshopItemCatalogEntry(Identifier.ofVanilla("gold_ingot"), 10, 2, false),
			new WorkshopItemCatalogEntry(Identifier.ofVanilla("iron_ingot"), 64, 1, false)
		), 2);

		assertEquals(3, catalog.totalDistinctItems());
		assertTrue(catalog.truncated());
		assertEquals(List.of(Identifier.ofVanilla("iron_ingot"), Identifier.ofVanilla("gold_ingot")),
			catalog.entries().stream().map(WorkshopItemCatalogEntry::itemId).toList());
		assertThrows(UnsupportedOperationException.class, () -> catalog.entries().add(catalog.entries().getFirst()));
	}

	@Test
	void invalidCatalogEntriesAreRejected() {
		assertThrows(IllegalArgumentException.class,
			() -> new WorkshopItemCatalogEntry(Identifier.ofVanilla("air"), 1, 1, false));
		assertThrows(IllegalArgumentException.class,
			() -> new WorkshopItemCatalogEntry(Identifier.ofVanilla("iron_ingot"), 0, 1, false));
		assertThrows(IllegalArgumentException.class,
			() -> new WorkshopItemCatalogEntry(Identifier.ofVanilla("iron_ingot"), 1, 0, false));
		assertThrows(IllegalArgumentException.class, () -> WorkshopItemCatalog.fromEntries(List.of(), -1));
	}
}
