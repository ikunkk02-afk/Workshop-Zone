package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.client.gui.screen.ingame.HandledScreen;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

public final class WorkshopSidebarPlacementRegistry {
	private static final Map<HandledScreen<?>, WorkshopSidebarPlacement> PLACEMENTS =
		Collections.synchronizedMap(new WeakHashMap<>());

	private WorkshopSidebarPlacementRegistry() {
	}

	public static void update(HandledScreen<?> screen, WorkshopSidebarPlacement placement) {
		if (screen != null && placement != null) {
			PLACEMENTS.put(screen, placement);
		}
	}

	public static Optional<WorkshopSidebarPlacement> get(HandledScreen<?> screen) {
		return Optional.ofNullable(PLACEMENTS.get(screen));
	}

	public static void remove(HandledScreen<?> screen) {
		PLACEMENTS.remove(screen);
	}
}
