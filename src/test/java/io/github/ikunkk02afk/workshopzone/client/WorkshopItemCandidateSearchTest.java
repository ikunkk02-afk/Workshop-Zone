package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopItemCandidateSearchTest {
	private static final WorkshopItemCandidateMetadata IRON_ZH = candidate("minecraft:iron_ingot", "铁锭");
	private static final WorkshopItemCandidateMetadata IRON_EN = candidate("test:iron_ingot", "Iron Ingot");
	private static final WorkshopItemCandidateMetadata RAW_IRON = candidate("create:raw_iron", "Raw Iron");
	private static final WorkshopItemCandidateMetadata COPPER = candidate("create:copper_ingot", "铜锭");

	@Test
	void localizedChineseAndEnglishNamesMatchCaseInsensitively() {
		assertEquals(List.of(IRON_ZH), search(List.of(IRON_ZH, IRON_EN), "铁锭").candidates());
		assertEquals(IRON_EN, search(List.of(IRON_ZH, IRON_EN), "IRON").candidates().getFirst());
	}

	@Test
	void exactRegistryIdRanksBeforeOtherMatches() {
		WorkshopItemCandidateSearch.MetadataResult result = search(
			List.of(RAW_IRON, IRON_EN, IRON_ZH), "minecraft:iron_ingot"
		);
		assertEquals(IRON_ZH, result.candidates().getFirst());
	}

	@Test
	void pathAndNamespaceFilterAreSupported() {
		assertEquals(List.of(IRON_EN, IRON_ZH), search(List.of(IRON_ZH, IRON_EN, RAW_IRON), "iron_ingot").candidates());
		assertEquals(List.of(COPPER), search(List.of(IRON_ZH, COPPER), "@create 铜").candidates());
	}

	@Test
	void resultLimitIsFiftyAndOrderingIsStable() {
		List<WorkshopItemCandidateMetadata> candidates = new ArrayList<>();
		for (int index = 59; index >= 0; index--) {
			candidates.add(candidate("test:item_" + index, "Item " + String.format("%02d", index)));
		}
		WorkshopItemCandidateSearch.MetadataResult result = search(candidates, "item");
		assertEquals(50, result.candidates().size());
		assertTrue(result.truncated());
		assertEquals("test:item_0", result.candidates().getFirst().itemId().toString());
	}

	@Test
	void emptySearchDoesNotReturnTheWholeRegistryAndAirIsInvalid() {
		assertTrue(search(List.of(IRON_ZH), "").candidates().isEmpty());
		assertFalse(WorkshopItemCandidate.isValidId(Identifier.ofVanilla("air")));
	}

	private static WorkshopItemCandidateSearch.MetadataResult search(List<WorkshopItemCandidateMetadata> candidates, String query) {
		return WorkshopItemCandidateSearch.searchMetadata(candidates, WorkshopItemSearchQuery.parse(query));
	}

	private static WorkshopItemCandidateMetadata candidate(String id, String name) {
		return new WorkshopItemCandidateMetadata(Identifier.of(id), name);
	}
}
