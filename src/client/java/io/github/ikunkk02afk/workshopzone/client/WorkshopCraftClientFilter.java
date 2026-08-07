package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftPreviewResultCode;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftMode;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftExecutionResultPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftPreviewPayload;
import net.minecraft.util.Identifier;

public final class WorkshopCraftClientFilter {
	private WorkshopCraftClientFilter() {
	}

	public static boolean acceptPreview(
		WorkshopCraftPreviewPayload payload,
		long sessionId,
		long revision,
		int syncId,
		boolean craftingScreen,
		boolean activeCraftingHandler,
		long lastAcceptedPreviewId
	) {
		return acceptPreview(
			payload.previewId(), payload.sessionId(), payload.revision(), payload.syncId(), payload.resultId(),
			sessionId, revision, syncId, craftingScreen, activeCraftingHandler, lastAcceptedPreviewId
		);
	}

	public static boolean acceptPreview(
		long previewId,
		long payloadSessionId,
		long payloadRevision,
		int payloadSyncId,
		WorkshopCraftPreviewResultCode resultId,
		long sessionId,
		long revision,
		int syncId,
		boolean craftingScreen,
		boolean activeCraftingHandler,
		long lastAcceptedPreviewId
	) {
		return acceptPreview(
			previewId, payloadSessionId, payloadRevision, payloadSyncId, resultId,
			sessionId, revision, syncId, craftingScreen, activeCraftingHandler, syncId, lastAcceptedPreviewId
		);
	}

	public static boolean acceptPreview(
		long previewId,
		long payloadSessionId,
		long payloadRevision,
		int payloadSyncId,
		WorkshopCraftPreviewResultCode resultId,
		long sessionId,
		long revision,
		int syncId,
		boolean craftingScreen,
		boolean activeCraftingHandler,
		int activeHandlerSyncId,
		long lastAcceptedPreviewId
	) {
		return activeCraftingHandler
			&& resultId == WorkshopCraftPreviewResultCode.AVAILABLE
			&& payloadSessionId == sessionId
			&& payloadRevision == revision
			&& payloadSyncId == syncId
			&& payloadSyncId == activeHandlerSyncId
			&& previewId > lastAcceptedPreviewId;
	}

	public static boolean canConfirm(boolean pending, long currentTick, long expiresAtTick) {
		return !pending && currentTick < expiresAtTick;
	}

	static boolean sameHandler(Object expectedHandler, Object activeHandler) {
		return expectedHandler == null || expectedHandler == activeHandler;
	}

	public static boolean acceptExecution(
		WorkshopCraftExecutionResultPayload payload,
		long previewId,
		long sessionId,
		int syncId,
		Identifier recipeId,
		WorkshopCraftMode craftMode,
		int plannedIterations,
		long lastAcceptedExecutionPreviewId
	) {
		return payload.previewId() == previewId
			&& payload.sessionId() == sessionId
			&& payload.syncId() == syncId
			&& payload.recipeId().equals(recipeId)
			&& payload.craftMode() == craftMode
			&& payload.plannedIterations() == plannedIterations
			&& payload.previewId() != lastAcceptedExecutionPreviewId;
	}

	public static int clampScrollIndex(int requested, int entryCount, int visibleRows) {
		return Math.max(0, Math.min(requested, Math.max(0, entryCount - Math.max(1, visibleRows))));
	}

	public static String titleKey(WorkshopCraftMode mode) {
		return mode == WorkshopCraftMode.BATCH
			? "gui.workshop_zone.craft.confirm.batch_title"
			: "gui.workshop_zone.craft.confirm.title";
	}

	public static String descriptionKey(WorkshopCraftMode mode) {
		return mode == WorkshopCraftMode.BATCH
			? "gui.workshop_zone.craft.confirm.batch_description"
			: "gui.workshop_zone.craft.confirm.description";
	}

	public static String acceptKey(WorkshopCraftMode mode) {
		return mode == WorkshopCraftMode.BATCH
			? "gui.workshop_zone.craft.confirm.accept_batch"
			: "gui.workshop_zone.craft.confirm.accept";
	}
}
