package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;
import net.minecraft.registry.RegistryKeys;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainerLabelRuleTest {
	private static final Identifier IRON = Identifier.ofVanilla("iron_ingot");
	private static final Identifier GOLD = Identifier.ofVanilla("gold_ingot");
	private static final Identifier SWORD = Identifier.ofVanilla("diamond_sword");
	private static final Identifier LOGS = Identifier.ofVanilla("logs");

	@Test
	void noneAllowsAnyNonEmptyItem() {
		assertTrue(ContainerLabelRule.NONE.canInsertItemId(GOLD));
	}

	@Test
	void exactItemAllowsSameItemAndRejectsDifferentItem() {
		ContainerLabelRule rule = ContainerLabelRule.exact(IRON);
		assertTrue(rule.canInsertItemId(IRON));
		assertFalse(rule.canInsertItemId(GOLD));
	}

	@Test
	void exactItemIgnoresQuantity() {
		ContainerLabelRule rule = ContainerLabelRule.exact(IRON);
		assertTrue(rule.canInsertItemId(IRON), "count 1 maps to the same item id");
		assertTrue(rule.canInsertItemId(IRON), "count 64 maps to the same item id");
	}

	@Test
	void exactItemIgnoresDamageAndComponents() {
		ContainerLabelRule rule = ContainerLabelRule.exact(SWORD);
		assertTrue(rule.canInsertItemId(SWORD), "plain sword uses the item id only");
		assertTrue(rule.canInsertItemId(SWORD), "damaged/component-bearing sword uses the same item id");
	}

	@Test
	void airCannotBecomeALabel() {
		assertThrows(IllegalArgumentException.class, () -> ContainerLabelRule.exact(Identifier.ofVanilla("air")));
	}

	@Test
	void itemTagAllowsMembersAndRejectsNonMembers() {
		ContainerLabelRule rule = ContainerLabelRule.itemTag(LOGS);
		assertTrue(rule.canInsert(false, Identifier.ofVanilla("oak_log"), tag -> tag.id().equals(LOGS)));
		assertFalse(rule.canInsert(false, Identifier.ofVanilla("oak_planks"), tag -> false));
	}

	@Test
	void emptyStackIsAlwaysAllowed() {
		ContainerLabelRule rule = ContainerLabelRule.itemTag(LOGS);
		assertTrue(rule.canInsert(true, null, tag -> false));
	}

	@Test
	void itemTagFactoryRejectsMissingIdAndInvalidState() {
		assertThrows(NullPointerException.class, () -> ContainerLabelRule.itemTag(null));
		assertThrows(IllegalArgumentException.class, () ->
			new ContainerLabelRule(ContainerLabelMode.ITEM_TAG, java.util.Optional.empty()));
		assertThrows(IllegalArgumentException.class, () ->
			new ContainerLabelRule(ContainerLabelMode.NONE, java.util.Optional.of(LOGS)));
	}

	@Test
	void itemTagCreatesCorrectRegistryKey() {
		assertEquals(RegistryKeys.ITEM, ContainerLabelRule.itemTag(LOGS).itemTagKey().orElseThrow().registry());
		assertEquals(LOGS, ContainerLabelRule.itemTag(LOGS).itemTagKey().orElseThrow().id());
	}

	@Test
	void modesUseStableUniqueIdentifiers() {
		assertEquals(Identifier.of("workshop_zone", "none"), ContainerLabelMode.NONE.id());
		assertEquals(Identifier.of("workshop_zone", "exact_item"), ContainerLabelMode.EXACT_ITEM.id());
		assertEquals(Identifier.of("workshop_zone", "item_tag"), ContainerLabelMode.ITEM_TAG.id());
		assertEquals(ContainerLabelMode.values().length, ContainerLabelMode.modes().size());
		assertThrows(UnsupportedOperationException.class, () -> ContainerLabelMode.modes().clear());
	}

	@Test
	void unknownModeIdFailsSafely() {
		assertTrue(ContainerLabelMode.fromId(Identifier.of("workshop_zone", "future_mode")).isEmpty());
	}
}
