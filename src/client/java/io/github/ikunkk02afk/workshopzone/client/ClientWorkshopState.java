package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.network.ClearWorkshopSessionPayload;
import io.github.ikunkk02afk.workshopzone.network.WorkshopNetworkEntry;
import io.github.ikunkk02afk.workshopzone.network.WorkshopSnapshotOrder;
import io.github.ikunkk02afk.workshopzone.network.WorkshopSnapshotPayload;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientWorkshopState {
	private static final Map<Identifier, ItemStack> ICON_CACHE = new HashMap<>();
	private static final Map<Identifier, ItemStack> LABEL_ICON_CACHE = new HashMap<>();

	private static ClientWorkshopSnapshot current;
	private static long acceptedSessionId = -1;
	private static long acceptedRevision = -1;
	private static boolean loaded;
	private static boolean cleared;

	private ClientWorkshopState() {
	}

	public static ClientWorkshopSnapshot current() {
		return current;
	}

	public static boolean isLoaded() {
		return loaded;
	}

	public static boolean wasClearedByServer() {
		return cleared;
	}

	public static boolean accept(MinecraftClient client, WorkshopSnapshotPayload payload) {
		if (client.player == null) {
			WorkshopZone.LOGGER.debug("Rejected workshop snapshot session {} revision {}: no client player", payload.sessionId(), payload.revision());
			return false;
		}
		if (client.world == null) {
			WorkshopZone.LOGGER.debug("Rejected workshop snapshot session {} revision {}: no client world", payload.sessionId(), payload.revision());
			return false;
		}
		if (client.player.currentScreenHandler.syncId != payload.syncId()) {
			WorkshopZone.LOGGER.debug(
				"Rejected workshop snapshot session {} revision {}: syncId {} does not match current {}",
				payload.sessionId(), payload.revision(), payload.syncId(), client.player.currentScreenHandler.syncId
			);
			return false;
		}
		if (!client.world.getRegistryKey().getValue().equals(payload.dimensionId())) {
			WorkshopZone.LOGGER.debug(
				"Rejected workshop snapshot session {} revision {}: dimension {} does not match current {}",
				payload.sessionId(), payload.revision(), payload.dimensionId(), client.world.getRegistryKey().getValue()
			);
			return false;
		}
		if (!WorkshopSnapshotOrder.isNewer(
			payload.sessionId(), payload.revision(), acceptedSessionId, acceptedRevision
		)) {
			WorkshopZone.LOGGER.debug(
				"Rejected workshop snapshot session {} revision {}: not newer than accepted session {} revision {}",
				payload.sessionId(), payload.revision(), acceptedSessionId, acceptedRevision
			);
			return false;
		}

		List<ClientWorkshopEntry> entries = new ArrayList<>(payload.entries().size());
		for (WorkshopNetworkEntry entry : payload.entries()) {
			Block block = Registries.BLOCK.getOrEmpty(entry.blockId()).orElse(Blocks.BARRIER);
			Text name = entry.customName().<Text>map(Text::literal).orElseGet(block::getName);
			entries.add(new ClientWorkshopEntry(
				entry.type(), entry.position(), entry.blockId(), entry.distanceSquared(),
				entry.container(), entry.workstation(), entry.customName().isPresent(), name,
				ICON_CACHE.computeIfAbsent(entry.blockId(), id -> createIcon(block)),
				entry.labelSummary(),
				entry.labelSummary().unavailable()
					? new ItemStack(Items.BARRIER)
					: entry.labelSummary().representativeItemId()
					.map(id -> LABEL_ICON_CACHE.computeIfAbsent(id, ClientWorkshopState::createItemIcon))
					.orElse(ItemStack.EMPTY)
			));
		}

		acceptedSessionId = payload.sessionId();
		acceptedRevision = payload.revision();
		loaded = true;
		cleared = false;
		current = new ClientWorkshopSnapshot(
			payload.sessionId(), payload.revision(), payload.syncId(), payload.dimensionId(), payload.scanCenter(), payload.openedEntryPosition(),
			payload.openedBlockType(), payload.totalEntryCount(), payload.containerCount(), payload.workstationCount(),
			payload.truncated(), entries
		);
		WorkshopZone.LOGGER.debug(
			"Accepted workshop snapshot session {} revision {} syncId {} with {} entries",
			payload.sessionId(), payload.revision(), payload.syncId(), entries.size()
		);
		return true;
	}

	public static void acceptClear(ClearWorkshopSessionPayload payload) {
		WorkshopZone.LOGGER.debug(
			"Received workshop session clear for session {} syncId {}", payload.sessionId(), payload.syncId()
		);
		if (payload.sessionId() < acceptedSessionId) {
			return;
		}
		acceptedSessionId = payload.sessionId();
		acceptedRevision = Long.MAX_VALUE;
		if (current == null || current.sessionId() <= payload.sessionId()) {
			current = null;
			loaded = false;
			cleared = true;
		}
	}

	public static void clearForScreen(int syncId) {
		if (current != null && current.syncId() == syncId) {
			current = null;
			loaded = false;
		}
	}

	public static void resetConnection() {
		current = null;
		acceptedSessionId = -1;
		acceptedRevision = -1;
		loaded = false;
		cleared = false;
		ICON_CACHE.clear();
		LABEL_ICON_CACHE.clear();
		ClientContainerLabelState.reset();
		ClientContainerLabelDetailsState.reset();
		ClientItemTagState.reset();
		ClientDepositState.reset();
	}

	private static ItemStack createIcon(Block block) {
		Item item = block.asItem();
		return item == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(item);
	}

	private static ItemStack createItemIcon(Identifier id) {
		Item item = Registries.ITEM.getOrEmpty(id).orElse(Items.BARRIER);
		return new ItemStack(item);
	}
}
