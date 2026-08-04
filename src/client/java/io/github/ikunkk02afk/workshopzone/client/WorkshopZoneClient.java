package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import io.github.ikunkk02afk.workshopzone.network.ClearWorkshopSessionPayload;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelDetailsPayload;
import io.github.ikunkk02afk.workshopzone.network.ContainerLabelEditResultPayload;
import io.github.ikunkk02afk.workshopzone.network.ItemTagCandidatesPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopItemSearchResultPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopSnapshotPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftExecutionResultPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftPreviewPayload;

public class WorkshopZoneClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RecipeViewerDetector.initialize();
		WorkshopClientConfigManager.initialize();
		ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
			@Override
			public Identifier getFabricId() {
				return WorkshopZone.id("item_candidate_names");
			}

			@Override
			public void reload(ResourceManager manager) {
				net.minecraft.client.MinecraftClient.getInstance().execute(() -> {
					ClientWorkshopSearchState.refreshLocalizedCandidates();
					ClientWorkshopCraftState.reset();
				});
			}
		});
		WorkshopHighlightRenderer.register();
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
		ClientPlayNetworking.registerGlobalReceiver(
			WorkshopItemSearchResultPayload.ID,
			(payload, context) -> context.client().execute(() -> ClientWorkshopSearchState.acceptNetwork(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
			WorkshopItemCatalogPayload.ID,
			(payload, context) -> context.client().execute(() -> ClientWorkshopSearchState.acceptCatalogNetwork(payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
			WorkshopCraftPreviewPayload.ID,
			(payload, context) -> context.client().execute(() -> ClientWorkshopCraftState.acceptPreview(context.client(), payload))
		);
		ClientPlayNetworking.registerGlobalReceiver(
			WorkshopCraftExecutionResultPayload.ID,
			(payload, context) -> context.client().execute(() -> ClientWorkshopCraftState.acceptExecution(context.client(), payload))
		);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			ClientWorkshopState.resetConnection();
			ClientWorkshopCraftState.reset();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientWorkshopState.resetConnection();
			ClientWorkshopCraftState.reset();
		});
		WorkshopScreenIntegration.register();
		WorkshopZone.LOGGER.debug("Workshop Zone client initialization complete");
	}
}
