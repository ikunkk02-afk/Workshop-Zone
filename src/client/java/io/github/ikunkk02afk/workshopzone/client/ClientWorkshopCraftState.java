package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.ConfirmWorkshopCraftPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftExecutionResultPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftPreviewPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.screen.CraftingScreenHandler;

public final class ClientWorkshopCraftState {
	private static WorkshopCraftPreviewPayload currentPreview;
	private static WorkshopCraftExecutionResultPayload lastExecutionResult;
	private static boolean pending;
	private static int scrollIndex;
	private static long expiresAtMs;
	private static long lastAcceptedPreviewId = -1;
	private static long lastAcceptedExecutionPreviewId = -1;
	private static long currentSessionId = -1;
	private static long currentRevision = -1;
	private static int currentSyncId = -1;

	private ClientWorkshopCraftState() {
	}

	public static boolean isVisible() {
		return currentPreview != null && lastExecutionResult == null;
	}

	public static boolean isPending() {
		return pending;
	}

	public static boolean isExpired(MinecraftClient client) {
		return currentPreview == null || System.currentTimeMillis() >= expiresAtMs;
	}

	public static WorkshopCraftPreviewPayload preview() {
		return currentPreview;
	}

	public static int scrollIndex() {
		return scrollIndex;
	}

	static void scroll(int delta, int visibleRows) {
		if (currentPreview == null) return;
		scrollIndex = WorkshopCraftClientFilter.clampScrollIndex(
			scrollIndex + delta,
			currentPreview.materials().size(),
			Math.max(1, visibleRows)
		);
	}

	public static void confirm(MinecraftClient client) {
		if (currentPreview == null || pending || isExpired(client)) return;
		pending = true;
		ClientPlayNetworking.send(new ConfirmWorkshopCraftPayload(currentPreview.previewId(), true));
	}

	public static void cancel(MinecraftClient client) {
		if (currentPreview == null) return;
		if (pending) {
			if (isExpired(client)) {
				reset();
			}
			return;
		}
		ClientPlayNetworking.send(new ConfirmWorkshopCraftPayload(currentPreview.previewId(), false));
		reset();
	}

	static void acceptPreview(MinecraftClient client, WorkshopCraftPreviewPayload payload) {
		if (payload == null) return;
		if (!WorkshopCraftClientFilter.acceptPreview(
			payload.previewId(), payload.sessionId(), payload.revision(), payload.syncId(), payload.resultId(),
			currentSessionId, currentRevision, currentSyncId,
			client.currentScreen instanceof CraftingScreen,
			client.player != null && client.player.currentScreenHandler instanceof CraftingScreenHandler,
			lastAcceptedPreviewId
		)) {
			return;
		}
		currentPreview = payload;
		lastExecutionResult = null;
		pending = false;
		scrollIndex = 0;
		lastAcceptedPreviewId = payload.previewId();
		expiresAtMs = System.currentTimeMillis() + payload.expiresInTicks() * 50L;
	}

	static void acceptExecution(MinecraftClient client, WorkshopCraftExecutionResultPayload payload) {
		if (payload == null || currentPreview == null) return;
		if (!WorkshopCraftClientFilter.acceptExecution(
			payload, currentPreview.previewId(), currentSessionId, currentSyncId,
			currentPreview.recipeId(), currentPreview.craftMode(), currentPreview.plannedIterations(),
			lastAcceptedExecutionPreviewId
		)) {
			return;
		}
		lastAcceptedExecutionPreviewId = payload.previewId();
		pending = false;
		lastExecutionResult = payload;
		currentPreview = null;
	}

	public static void onScreenChanged(Screen current) {
		if (current == null || !(current instanceof CraftingScreen)) {
			reset();
		}
	}

	public static void clearForSession(long sessionId, long revision, int syncId) {
		if (sessionId != currentSessionId || revision != currentRevision || syncId != currentSyncId) {
			currentSessionId = sessionId;
			currentRevision = revision;
			currentSyncId = syncId;
			lastAcceptedPreviewId = -1;
			lastAcceptedExecutionPreviewId = -1;
			clearTransient();
		}
	}

	public static void reset() {
		clearTransient();
	}

	public static void resetConnection() {
		currentSessionId = -1;
		currentRevision = -1;
		currentSyncId = -1;
		lastAcceptedPreviewId = -1;
		lastAcceptedExecutionPreviewId = -1;
		clearTransient();
	}

	private static void clearTransient() {
		currentPreview = null;
		lastExecutionResult = null;
		pending = false;
		scrollIndex = 0;
		expiresAtMs = 0;
	}

}
