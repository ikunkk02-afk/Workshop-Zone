package io.github.ikunkk02afk.workshopzone.scan;

public enum WorkshopBlockType {
	CHEST("block.workshop_zone.chest", true, false),
	TRAPPED_CHEST("block.workshop_zone.trapped_chest", true, false),
	BARREL("block.workshop_zone.barrel", true, false),
	CRAFTING_TABLE("block.workshop_zone.crafting_table", false, true),
	FURNACE("block.workshop_zone.furnace", false, true),
	BLAST_FURNACE("block.workshop_zone.blast_furnace", false, true),
	SMOKER("block.workshop_zone.smoker", false, true);

	private final String translationKey;
	private final boolean container;
	private final boolean processingDevice;

	WorkshopBlockType(String translationKey, boolean container, boolean processingDevice) {
		this.translationKey = translationKey;
		this.container = container;
		this.processingDevice = processingDevice;
	}

	public String translationKey() {
		return translationKey;
	}

	public boolean isContainer() {
		return container;
	}

	public boolean isProcessingDevice() {
		return processingDevice;
	}
}
