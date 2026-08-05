package io.github.ikunkk02afk.workshopzone.craft;

import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopCraftModeTest {
	@Test
	void modesUseStableIdentifiersAndRoundTrip() {
		assertEquals(Identifier.of("workshop_zone", "single"), WorkshopCraftMode.SINGLE.id());
		assertEquals(Identifier.of("workshop_zone", "batch"), WorkshopCraftMode.BATCH.id());
		assertEquals(WorkshopCraftMode.SINGLE, WorkshopCraftMode.fromId(WorkshopCraftMode.SINGLE.id()).orElseThrow());
		assertEquals(WorkshopCraftMode.BATCH, WorkshopCraftMode.fromId(WorkshopCraftMode.BATCH.id()).orElseThrow());
	}

	@Test
	void unknownModeIsRejectedSafely() {
		assertTrue(WorkshopCraftMode.fromId(Identifier.of("other", "batch")).isEmpty());
	}
}
