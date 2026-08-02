package io.github.ikunkk02afk.workshopzone.scan;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.minecraft.util.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public enum WorkshopBlockType {
	CHEST("chest", "block.workshop_zone.chest", true, false),
	TRAPPED_CHEST("trapped_chest", "block.workshop_zone.trapped_chest", true, false),
	BARREL("barrel", "block.workshop_zone.barrel", true, false),
	CRAFTING_TABLE("crafting_table", "block.workshop_zone.crafting_table", false, true),
	FURNACE("furnace", "block.workshop_zone.furnace", false, true),
	BLAST_FURNACE("blast_furnace", "block.workshop_zone.blast_furnace", false, true),
	SMOKER("smoker", "block.workshop_zone.smoker", false, true),
	SMITHING_TABLE("smithing_table", "block.workshop_zone.smithing_table", false, true),
	ANVIL("anvil", "block.workshop_zone.anvil", false, true),
	STONECUTTER("stonecutter", "block.workshop_zone.stonecutter", false, true),
	GRINDSTONE("grindstone", "block.workshop_zone.grindstone", false, true),
	LOOM("loom", "block.workshop_zone.loom", false, true),
	CARTOGRAPHY_TABLE("cartography_table", "block.workshop_zone.cartography_table", false, true),
	BREWING_STAND("brewing_stand", "block.workshop_zone.brewing_stand", false, true),
	ENCHANTING_TABLE("enchanting_table", "block.workshop_zone.enchanting_table", false, true);

	private static final Map<Identifier, WorkshopBlockType> BY_NETWORK_ID = createNetworkIdMap();

	private final Identifier networkId;
	private final String translationKey;
	private final boolean container;
	private final boolean workstation;

	WorkshopBlockType(String networkPath, String translationKey, boolean container, boolean workstation) {
		this.networkId = WorkshopZone.id(networkPath);
		this.translationKey = translationKey;
		this.container = container;
		this.workstation = workstation;
	}

	public Identifier networkId() {
		return networkId;
	}

	public static Optional<WorkshopBlockType> fromNetworkId(Identifier networkId) {
		return Optional.ofNullable(BY_NETWORK_ID.get(networkId));
	}

	public static Map<Identifier, WorkshopBlockType> networkTypes() {
		return BY_NETWORK_ID;
	}

	public String translationKey() {
		return translationKey;
	}

	public boolean isContainer() {
		return container;
	}

	public boolean isWorkstation() {
		return workstation;
	}

	public boolean isProcessingDevice() {
		return isWorkstation();
	}

	private static Map<Identifier, WorkshopBlockType> createNetworkIdMap() {
		Map<Identifier, WorkshopBlockType> types = new LinkedHashMap<>();
		for (WorkshopBlockType type : values()) {
			WorkshopBlockType previous = types.putIfAbsent(type.networkId, type);
			if (previous != null) {
				throw new IllegalStateException(
					"Duplicate workshop block type network id " + type.networkId + " for " + previous + " and " + type
				);
			}
		}
		return Map.copyOf(types);
	}
}
