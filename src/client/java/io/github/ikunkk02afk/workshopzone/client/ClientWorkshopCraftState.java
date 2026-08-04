package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftExecutionResultPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftPreviewPayload;
import net.minecraft.client.MinecraftClient;

public final class ClientWorkshopCraftState {
	private ClientWorkshopCraftState() {
	}

	public static void acceptPreview(MinecraftClient client, WorkshopCraftPreviewPayload payload) {
		ClientWorkshopCraftOverlay.receivePreview(payload);
	}

	public static void acceptExecution(MinecraftClient client, WorkshopCraftExecutionResultPayload payload) {
		ClientWorkshopCraftOverlay.receiveExecution(payload);
	}

	public static void reset() {
		ClientWorkshopCraftOverlay.clear();
	}
}
