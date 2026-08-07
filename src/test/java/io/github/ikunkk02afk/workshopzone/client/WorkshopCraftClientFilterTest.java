package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftExecutionResultCode;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftMode;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftPreviewResultCode;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftExecutionResultPayload;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopCraftClientFilterTest {
	@Test
	void activeCraftingHandlerAcceptsPreviewWhileRecipeViewerScreenIsOnTop() {
		assertTrue(acceptPreview(11, 4, 9, false, true, 9, 50));
		assertFalse(acceptPreview(12, 4, 9, false, true, 9, 50));
		assertFalse(acceptPreview(11, 5, 9, false, true, 9, 50));
		assertFalse(acceptPreview(11, 4, 10, false, true, 9, 50));
		assertFalse(acceptPreview(11, 4, 9, false, true, 10, 50));
		assertFalse(acceptPreview(11, 4, 9, true, false, 9, 50));
		assertFalse(acceptPreview(11, 4, 9, false, true, 9, 51));
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
	void boundPreviewOnlySurvivesTheSameHandlerIdentity() {
		Object activeHandler = new Object();
		assertTrue(WorkshopCraftClientFilter.sameHandler(null, activeHandler));
		assertTrue(WorkshopCraftClientFilter.sameHandler(activeHandler, activeHandler));
		assertFalse(WorkshopCraftClientFilter.sameHandler(new Object(), activeHandler));
	}

	@Test
	void singleAndBatchConfirmationUseDistinctStableTranslationKeys() {
		assertEquals("gui.workshop_zone.craft.confirm.title", WorkshopCraftClientFilter.titleKey(WorkshopCraftMode.SINGLE));
		assertEquals("gui.workshop_zone.craft.confirm.description", WorkshopCraftClientFilter.descriptionKey(WorkshopCraftMode.SINGLE));
		assertEquals("gui.workshop_zone.craft.confirm.accept", WorkshopCraftClientFilter.acceptKey(WorkshopCraftMode.SINGLE));
		assertEquals("gui.workshop_zone.craft.confirm.batch_title", WorkshopCraftClientFilter.titleKey(WorkshopCraftMode.BATCH));
		assertEquals("gui.workshop_zone.craft.confirm.batch_description", WorkshopCraftClientFilter.descriptionKey(WorkshopCraftMode.BATCH));
		assertEquals("gui.workshop_zone.craft.confirm.accept_batch", WorkshopCraftClientFilter.acceptKey(WorkshopCraftMode.BATCH));
	}

	@Test
	void duplicateOrMismatchedExecutionResultsAreIgnored() {
		WorkshopCraftExecutionResultPayload result = new WorkshopCraftExecutionResultPayload(
			51, 11, 9, WorkshopCraftExecutionResultCode.SUCCESS,
			Identifier.ofVanilla("crafting_table"), WorkshopCraftMode.SINGLE, 1, 4, 2, 2, 1
		);
		Identifier recipeId = Identifier.ofVanilla("crafting_table");
		assertTrue(WorkshopCraftClientFilter.acceptExecution(result, 51, 11, 9, recipeId, WorkshopCraftMode.SINGLE, 1, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 52, 11, 9, recipeId, WorkshopCraftMode.SINGLE, 1, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 51, 12, 9, recipeId, WorkshopCraftMode.SINGLE, 1, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 51, 11, 10, recipeId, WorkshopCraftMode.SINGLE, 1, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 51, 11, 9, Identifier.ofVanilla("stick"), WorkshopCraftMode.SINGLE, 1, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 51, 11, 9, recipeId, WorkshopCraftMode.BATCH, 2, -1));
		assertFalse(WorkshopCraftClientFilter.acceptExecution(result, 51, 11, 9, recipeId, WorkshopCraftMode.SINGLE, 1, 51));
	}

	private static boolean acceptPreview(
		long sessionId,
		long revision,
		int syncId,
		boolean craftingScreen,
		boolean activeCraftingHandler,
		int activeHandlerSyncId,
		long lastPreviewId
	) {
		return WorkshopCraftClientFilter.acceptPreview(
			51, 11, 4, 9, WorkshopCraftPreviewResultCode.AVAILABLE,
			sessionId, revision, syncId, craftingScreen, activeCraftingHandler, activeHandlerSyncId, lastPreviewId
		);
	}
}
