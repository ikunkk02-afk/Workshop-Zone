package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
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

public final class WorkshopScreenIntegration {
	private static boolean expanded = true;

	private WorkshopScreenIntegration() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof HandledScreen<?> handledScreen) || !isSupported(screen)) {
				return;
			}
			WorkshopScreenController controller = new WorkshopScreenController(handledScreen);
			WorkshopSidebarWidget sidebar = WorkshopWidgetRegistry.replaceSingle(
				Screens.getButtons(screen),
				WorkshopSidebarWidget.class::isInstance,
				() -> new WorkshopSidebarWidget(handledScreen, !(screen instanceof GenericContainerScreen), controller)
			);
			controller.attachSidebar(sidebar);
			Screens.getButtons(screen).add(controller.searchField());
			WorkshopCraftConfirmationOverlay craftOverlay = screen instanceof CraftingScreen craftingScreen
				? new WorkshopCraftConfirmationOverlay(craftingScreen, controller) : null;
			if (craftOverlay != null) {
				WorkshopCraftInputOverlayRegistry.put(screen, craftOverlay);
				ScreenEvents.afterTick(screen).register(current -> ClientWorkshopCraftState.onScreenChanged(current));
				ScreenMouseEvents.allowMouseScroll(screen).register((current, horizontal, vertical, mouseX, mouseY) -> {
					if (!ClientWorkshopCraftState.isVisible()) {
						return true;
					}
					craftOverlay.mouseScrolled(horizontal, vertical);
					return false;
				});
				ScreenMouseEvents.allowMouseRelease(screen).register((current, mouseX, mouseY, button) ->
					!ClientWorkshopCraftState.isVisible()
				);
			}
			ScreenKeyboardEvents.allowKeyPress(screen).register((current, key, scanCode, modifiers) -> {
				if (current instanceof CraftingScreen && ClientWorkshopCraftState.isVisible()) {
					if (key == 256) {
						ClientWorkshopCraftState.cancel(client);
					}
					return false;
				}
				if (!controller.shouldCaptureKey(key)) {
					return true;
				}
				controller.keyPressed(key, scanCode, modifiers);
				return false;
			});
			ScreenMouseEvents.beforeMouseClick(screen).register((current, mouseX, mouseY, button) ->
				controller.clickedOutsideWorkshop(mouseX, mouseY)
			);
			WorkshopZone.LOGGER.debug(
				"Added workshop screen controller to {} with syncId {}",
				screen.getClass().getName(), handledScreen.getScreenHandler().syncId
			);
			ScreenEvents.remove(screen).register(removed -> {
				WorkshopZone.LOGGER.debug(
					"Removed workshop screen controller from {} with syncId {}",
					removed.getClass().getName(), handledScreen.getScreenHandler().syncId
				);
				controller.removed();
				WorkshopCraftInputOverlayRegistry.remove(screen);
				WorkshopSidebarPlacementRegistry.remove(handledScreen);
				// Recipe viewers temporarily replace CraftingScreen while its server menu stays open.
				// The server's ClearWorkshopSessionPayload is the authority for clearing this session.
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
