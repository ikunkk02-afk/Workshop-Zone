package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContainerLabelRuleTest {
	private static final Identifier IRON = Identifier.ofVanilla("iron_ingot");
	private static final Identifier GOLD = Identifier.ofVanilla("gold_ingot");
	private static final Identifier SWORD = Identifier.ofVanilla("diamond_sword");

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
	void modesUseStableUniqueIdentifiers() {
		assertEquals(Identifier.of("workshop_zone", "none"), ContainerLabelMode.NONE.id());
		assertEquals(Identifier.of("workshop_zone", "exact_item"), ContainerLabelMode.EXACT_ITEM.id());
		assertEquals(ContainerLabelMode.values().length, ContainerLabelMode.modes().size());
		assertThrows(UnsupportedOperationException.class, () -> ContainerLabelMode.modes().clear());
	}

	@Test
	void unknownModeIdFailsSafely() {
		assertTrue(ContainerLabelMode.fromId(Identifier.of("workshop_zone", "future_mode")).isEmpty());
	}
}
