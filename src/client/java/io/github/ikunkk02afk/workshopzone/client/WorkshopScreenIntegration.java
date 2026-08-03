package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BlastFurnaceScreen;
import net.minecraft.client.gui.screen.ingame.BrewingStandScreen;
import net.minecraft.client.gui.screen.ingame.CartographyTableScreen;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.FurnaceScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.GrindstoneScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.LoomScreen;
import net.minecraft.client.gui.screen.ingame.SmithingScreen;
import net.minecraft.client.gui.screen.ingame.SmokerScreen;
import net.minecraft.client.gui.screen.ingame.StonecutterScreen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;

public final class WorkshopScreenIntegration {
	private static boolean expanded = true;

	private WorkshopScreenIntegration() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof HandledScreen<?> handledScreen) || !isSupported(screen)) {
				return;
			}
			WorkshopWidgetRegistry.replaceSingle(
				Screens.getButtons(screen),
				WorkshopSidebarWidget.class::isInstance,
				() -> new WorkshopSidebarWidget(handledScreen, !(screen instanceof GenericContainerScreen))
			);
			WorkshopZone.LOGGER.debug(
				"Added workshop sidebar to screen {} with syncId {}",
				screen.getClass().getName(), handledScreen.getScreenHandler().syncId
			);
			ScreenEvents.remove(screen).register(removed -> {
				WorkshopZone.LOGGER.debug(
					"Removed workshop sidebar from screen {} with syncId {}",
					removed.getClass().getName(), handledScreen.getScreenHandler().syncId
				);
				WorkshopSidebarPlacementRegistry.remove(handledScreen);
				ClientWorkshopState.clearForScreen(handledScreen.getScreenHandler().syncId);
			});
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
			|| screen instanceof SmokerScreen
			|| screen instanceof SmithingScreen
			|| screen instanceof AnvilScreen
			|| screen instanceof StonecutterScreen
			|| screen instanceof GrindstoneScreen
			|| screen instanceof LoomScreen
			|| screen instanceof CartographyTableScreen
			|| screen instanceof BrewingStandScreen
			|| screen instanceof EnchantmentScreen;
	}
}
