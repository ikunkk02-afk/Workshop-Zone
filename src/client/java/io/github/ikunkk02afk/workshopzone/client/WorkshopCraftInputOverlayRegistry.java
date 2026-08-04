package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.client.gui.screen.Screen;

import java.util.Map;
import java.util.WeakHashMap;

public final class WorkshopCraftInputOverlayRegistry {
	private static final Map<Screen, WorkshopCraftConfirmationOverlay> OVERLAYS = new WeakHashMap<>();
	private static Screen currentScreen;

	private WorkshopCraftInputOverlayRegistry() {
	}

	public static void put(Screen screen, WorkshopCraftConfirmationOverlay overlay) {
		WorkshopCraftConfirmationOverlay replaced = OVERLAYS.put(screen, overlay);
		if (replaced != null && replaced != overlay) {
			replaced.removed();
		}
		currentScreen = screen;
	}

	public static WorkshopCraftConfirmationOverlay current() {
		return currentScreen == null ? null : OVERLAYS.get(currentScreen);
	}

	public static void remove(Screen screen) {
		WorkshopCraftConfirmationOverlay removed = OVERLAYS.remove(screen);
		if (removed != null) {
			removed.removed();
		}
		if (currentScreen == screen) {
			currentScreen = null;
		}
	}
}
