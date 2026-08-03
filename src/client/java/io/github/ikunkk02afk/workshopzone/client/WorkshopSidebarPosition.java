package io.github.ikunkk02afk.workshopzone.client;

import java.util.Locale;

public enum WorkshopSidebarPosition {
	AUTO("auto"),
	RIGHT("right"),
	LEFT("left"),
	TOP("top"),
	BOTTOM("bottom"),
	CUSTOM("custom");

	private final String id;

	WorkshopSidebarPosition(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static WorkshopSidebarPosition fromId(String id) {
		if (id == null) {
			return AUTO;
		}
		String normalized = id.trim().toLowerCase(Locale.ROOT);
		for (WorkshopSidebarPosition position : values()) {
			if (position.id.equals(normalized)) {
				return position;
			}
		}
		return AUTO;
	}
}
