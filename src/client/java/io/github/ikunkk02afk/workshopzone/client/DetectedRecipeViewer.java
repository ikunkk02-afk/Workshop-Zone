package io.github.ikunkk02afk.workshopzone.client;

public enum DetectedRecipeViewer {
	JEI("jei", "workshop_zone:jei", "JEI"),
	EMI("emi", "workshop_zone:emi", "EMI"),
	REI("roughlyenoughitems", "workshop_zone:rei", "REI");

	private final String modId;
	private final String stableId;
	private final String displayName;

	DetectedRecipeViewer(String modId, String stableId, String displayName) {
		this.modId = modId;
		this.stableId = stableId;
		this.displayName = displayName;
	}

	public String modId() {
		return modId;
	}

	public String stableId() {
		return stableId;
	}

	public String displayName() {
		return displayName;
	}
}
