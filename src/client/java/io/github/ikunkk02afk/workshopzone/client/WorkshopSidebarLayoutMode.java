package io.github.ikunkk02afk.workshopzone.client;

public enum WorkshopSidebarLayoutMode {
	STANDARD,
	COMPACT,
	NARROW;

	public static WorkshopSidebarLayoutMode forWidth(int panelWidth) {
		if (panelWidth >= 224) {
			return STANDARD;
		}
		if (panelWidth >= 176) {
			return COMPACT;
		}
		return NARROW;
	}
}
