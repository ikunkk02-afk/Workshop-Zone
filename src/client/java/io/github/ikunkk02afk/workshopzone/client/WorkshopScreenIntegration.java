package io.github.ikunkk02afk.workshopzone.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BlastFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.FurnaceScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.SmokerScreen;

public final class WorkshopScreenIntegration {
	private static boolean expanded = true;

	private WorkshopScreenIntegration() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof HandledScreen<?> handledScreen) || !isSupported(screen)) {
				return;
			}
			Screens.getButtons(screen).removeIf(WorkshopSidebarWidget.class::isInstance);
			Screens.getButtons(screen).add(new WorkshopSidebarWidget(handledScreen));
			ScreenEvents.remove(screen).register(removed ->
				ClientWorkshopState.clearForScreen(handledScreen.getScreenHandler().syncId)
			);
		});
	}

	static boolean isExpanded() {
		return expanded;
	}

	static void setExpanded(boolean value) {
		expanded = value;
	}

	private static boolean isSupported(Screen screen) {
		return screen instanceof GenericContainerScreen
			|| screen instanceof CraftingScreen
			|| screen instanceof FurnaceScreen
			|| screen instanceof BlastFurnaceScreen
			|| screen instanceof SmokerScreen;
	}
}
