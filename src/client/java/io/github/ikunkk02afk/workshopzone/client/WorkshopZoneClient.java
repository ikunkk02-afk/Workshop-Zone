package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import io.github.ikunkk02afk.workshopzone.network.ClearWorkshopSessionPayload;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelDetailsPayload;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResultPayload;
import io.github.ikunkk02afk.workshopzone.network.ItemTagCandidatesPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopSnapshotPayload;

public class WorkshopZoneClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(WorkshopSnapshotPayload.ID, (payload, context) -> {
			WorkshopZone.LOGGER.debug(
				"Received workshop snapshot session {} revision {} syncId {} with {} entries",
				payload.sessionId(), payload.revision(), payload.syncId(), payload.entries().size()
			);
			context.client().execute(() -> ClientWorkshopState.accept(context.client(), payload));
		});
		ClientPlayNetworking.registerGlobalReceiver(ClearWorkshopSessionPayload.ID, (payload, context) ->
			context.client().execute(() -> ClientWorkshopState.acceptClear(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(ContainerLabelEditResultPayload.ID, (payload, context) ->
			context.client().execute(() -> ClientContainerLabelState.accept(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(ContainerLabelDetailsPayload.ID, (payload, context) ->
			context.client().execute(() -> ClientContainerLabelDetailsState.accept(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(ItemTagCandidatesPayload.ID, (payload, context) ->
			context.client().execute(() -> ClientItemTagState.accept(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
			io.github.ikunkk02afk.workshopzone.network.WorkshopDepositResultPayload.ID,
			(payload, context) -> context.client().execute(() -> ClientDepositState.accept(payload))
		);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientWorkshopState.resetConnection());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientWorkshopState.resetConnection());
		WorkshopScreenIntegration.register();
		WorkshopZone.LOGGER.debug("Workshop Zone client initialization complete");
	}
}
