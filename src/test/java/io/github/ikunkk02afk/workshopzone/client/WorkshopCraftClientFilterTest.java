package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftExecutionResultCode;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftPreviewResultCode;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftExecutionResultPayload;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopCraftClientFilterTest {
	@Test
	void onlyCurrentCraftingScreenSessionAndNewerPreviewAreAccepted() {
		assertTrue(acceptPreview(11, 4, 9, true, 50));
		assertFalse(acceptPreview(12, 4, 9, true, 50));
		assertFalse(acceptPreview(11, 5, 9, true, 50));
		assertFalse(acceptPreview(11, 4, 10, true, 50));
		assertFalse(acceptPreview(11, 4, 9, false, 50));
		assertFalse(acceptPreview(11, 4, 9, true, 51));
	}

	@Test
	void expiredPreviewCannotBeConfirmedAndScrollIsClamped() {
		assertFalse(WorkshopCraftClientFilter.canConfirm(false, 120, 120));
		assertFalse(WorkshopCraftClientFilter.canConfirm(true, 119, 120));
		assertTrue(WorkshopCraftClientFilter.canConfirm(false, 119, 120));
		assertEquals(0, WorkshopCraftClientFilter.clampScrollIndex(-3, 9, 3));
		assertEquals(6, WorkshopCraftClientFilter.clampScrollIndex(20, 9, 3));
		assertEquals(0, WorkshopCraftClientFilter.clampScrollIndex(2, 2, 3));
	}

	@Test
	void duplicateOrMismatchedExecutionResultsAreIgnored() {
		WorkshopCraftExecutionResultPayload result = new WorkshopCraftExecutionResultPayload(
			51, 11, 9, WorkshopCraftExecutionResultCode.SUCCESS,
			Identifier.ofVanilla("crafting_table"), 4, 2, 2, 1
		);
		assertTrue(WorkshopCraftClientFilter.acceptExecution(result, 51, 11, 9, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 52, 11, 9, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 51, 12, 9, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 51, 11, 10, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 51, 11, 9, 51));
	}

	private static boolean acceptPreview(long sessionId, long revision, int syncId, boolean crafting, long lastPreviewId) {
		return WorkshopCraftClientFilter.acceptPreview(
			51, 11, 4, 9, WorkshopCraftPreviewResultCode.AVAILABLE,
			sessionId, revision, syncId, crafting, lastPreviewId
		);
	}
}
