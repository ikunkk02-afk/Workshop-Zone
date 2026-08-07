package io.github.ikunkk02afk.workshopzone.client.compat.jei;

import io.github.ikunkk02afk.workshopzone.client.WorkshopSidebarPlacementRegistry;
import io.github.ikunkk02afk.workshopzone.client.WorkshopSidebarWidget;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.Rect2i;
import net.minecraft.util.Identifier;

import java.util.List;

@JeiPlugin
public final class WorkshopZoneJeiPlugin implements IModPlugin {
	private static final Identifier ID = Identifier.of("workshop_zone", "sidebar_areas");

	@Override
	public Identifier getPluginUid() {
		return ID;
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		registration.addRecipeTransferHandler(
			new WorkshopZoneJeiCraftingTransferHandler(registration.getTransferHelper()),
			RecipeTypes.CRAFTING
		);
	}

	@Override
	public void registerGuiHandlers(IGuiHandlerRegistration registration) {
		registration.addGenericGuiContainerHandler(HandledScreen.class, new IGuiContainerHandler<HandledScreen<?>>() {
			@Override
			public List<Rect2i> getGuiExtraAreas(HandledScreen<?> screen) {
				return Screens.getButtons(screen).stream()
					.filter(WorkshopSidebarWidget.class::isInstance)
					.map(WorkshopSidebarWidget.class::cast)
					.findFirst()
					.flatMap(WorkshopSidebarWidget::currentPlacementForCompatibility)
					.or(() -> WorkshopSidebarPlacementRegistry.get(screen))
					.map(placement -> placement.exclusionArea())
					.map(area -> List.of(new Rect2i(area.left(), area.top(), area.width(), area.height())))
					.orElseGet(List::of);
			}
		});
	}
}
