package io.github.ikunkk02afk.workshopzone.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import io.github.ikunkk02afk.workshopzone.network.ClearWorkshopSessionPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopSnapshotPayload;

public class WorkshopZoneClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(WorkshopSnapshotPayload.ID, (payload, context) ->
			context.client().execute(() -> ClientWorkshopState.accept(context.client(), payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(ClearWorkshopSessionPayload.ID, (payload, context) ->
			context.client().execute(() -> ClientWorkshopState.acceptClear(payload))
		);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientWorkshopState.resetConnection());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientWorkshopState.resetConnection());
		WorkshopScreenIntegration.register();
	}
}
