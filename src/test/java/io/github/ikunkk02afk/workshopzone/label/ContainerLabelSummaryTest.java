package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerLabelSummaryTest {
	@Test
	void exactItemSummaryHasOnePreview() {
		ContainerLabelSummary summary = ContainerLabelSummary.of(
			ContainerLabelRule.exactItem(id("iron_ingot"))
		);

		assertEquals(List.of(id("iron_ingot")), summary.previewItemIds());
		assertEquals(Optional.of(id("iron_ingot")), summary.representativeItemId());
	}

	@Test
	void whitelistPreviewCountTracksOneTwoAndFourItems() {
		assertEquals(1, whitelistItems(1).previewItemIds().size());
		assertEquals(2, whitelistItems(2).previewItemIds().size());
		assertEquals(4, whitelistItems(4).previewItemIds().size());
	}

	@Test
	void sixEntryWhitelistKeepsStableFourItemPrefix() {
		ContainerLabelSummary summary = whitelistItems(6);

		assertEquals(List.of(id("item_0"), id("item_1"), id("item_2"), id("item_3")), summary.previewItemIds());
		assertEquals(6, summary.whitelistEntryCount());
	}

	@Test
	void duplicateTagRepresentativeIsRemovedWithoutChangingOrder() {
		ContainerLabelRule rule = ContainerLabelRule.whitelist(List.of(
			ContainerLabelEntry.item(id("oak_log")),
			ContainerLabelEntry.item(id("stone")),
			ContainerLabelEntry.itemTag(id("logs"))
		));

		ContainerLabelSummary summary = ContainerLabelSummary.whitelist(
			rule, false, tag -> Optional.of(id("oak_log"))
		);

		assertEquals(List.of(id("oak_log"), id("stone")), summary.previewItemIds());
	}

	@Test
	void unavailableTagsAreExcludedWhileValidEntriesRemainUsable() {
		ContainerLabelRule rule = ContainerLabelRule.whitelist(List.of(
			ContainerLabelEntry.item(id("iron_ingot")),
			ContainerLabelEntry.itemTag(id("missing")),
			ContainerLabelEntry.itemTag(id("logs"))
		));
		Map<Identifier, Identifier> representatives = Map.of(id("logs"), id("oak_log"));

		ContainerLabelSummary summary = ContainerLabelSummary.whitelist(
			rule, false, tag -> Optional.ofNullable(representatives.get(tag))
		);

		assertEquals(List.of(id("iron_ingot"), id("oak_log")), summary.previewItemIds());
		assertEquals(1, summary.unavailableEntryCount());
		assertTrue(summary.partiallyUnavailable());
		assertFalse(summary.unavailable());
	}

	@Test
	void allUnavailableTagsProduceNoNormalPreview() {
		ContainerLabelRule rule = ContainerLabelRule.whitelist(List.of(
			ContainerLabelEntry.itemTag(id("missing_a")),
			ContainerLabelEntry.itemTag(id("missing_b"))
		));

		ContainerLabelSummary summary = ContainerLabelSummary.whitelist(rule, false, tag -> Optional.empty());

		assertTrue(summary.previewItemIds().isEmpty());
		assertEquals(2, summary.unavailableEntryCount());
		assertTrue(summary.unavailable());
	}

	@Test
	void previewListIsImmutableAndRejectsAirOrMoreThanFourEntries() {
		List<Identifier> mutable = new ArrayList<>(List.of(id("iron_ingot")));
		ContainerLabelSummary summary = new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(), mutable, 1, 0, false, false
		);
		mutable.clear();

		assertEquals(List.of(id("iron_ingot")), summary.previewItemIds());
		assertThrows(UnsupportedOperationException.class, () -> summary.previewItemIds().add(id("gold_ingot")));
		assertThrows(IllegalArgumentException.class, () -> new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(), List.of(Identifier.ofVanilla("air")),
			1, 0, false, false
		));
		assertThrows(IllegalArgumentException.class, () -> new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(), List.of(
				id("item_0"), id("item_1"), id("item_2"), id("item_3"), id("item_4")
			), 5, 0, false, false
		));
		assertThrows(IllegalArgumentException.class, () -> new ContainerLabelSummary(
			ContainerLabelMode.WHITELIST, Optional.empty(), Optional.empty(), List.of(
				id("item_0"), id("item_1")
			), 1, 0, false, false
		));
	}

	private static ContainerLabelSummary whitelistItems(int count) {
		List<ContainerLabelEntry> entries = new ArrayList<>();
		for (int index = 0; index < count; index++) {
			entries.add(ContainerLabelEntry.item(id("item_" + index)));
		}
		return ContainerLabelSummary.whitelist(ContainerLabelRule.whitelist(entries), false, tag -> Optional.empty());
	}

	private static Identifier id(String path) {
		return Identifier.of("test", path);
	}
}
