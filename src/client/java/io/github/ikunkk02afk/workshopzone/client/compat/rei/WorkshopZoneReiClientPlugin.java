package io.github.ikunkk02afk.workshopzone.client.compat.rei;

import io.github.ikunkk02afk.workshopzone.client.WorkshopSidebarPlacementRegistry;
import io.github.ikunkk02afk.workshopzone.client.WorkshopSidebarWidget;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.screen.ExclusionZones;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

import java.util.List;

public final class WorkshopZoneReiClientPlugin implements REIClientPlugin {
	@Override
	public void registerTransferHandlers(TransferHandlerRegistry registry) {
		registry.register(new WorkshopZoneReiCraftingTransferHandler());
	}

	@Override
	public void registerExclusionZones(ExclusionZones zones) {
		zones.register(HandledScreen.class, screen -> Screens.getButtons(screen).stream()
			.filter(WorkshopSidebarWidget.class::isInstance)
			.map(WorkshopSidebarWidget.class::cast)
			.findFirst()
			.flatMap(WorkshopSidebarWidget::currentPlacementForCompatibility)
			.or(() -> WorkshopSidebarPlacementRegistry.get(screen))
			.map(placement -> placement.exclusionArea())
			.map(area -> List.of(new Rectangle(area.left(), area.top(), area.width(), area.height())))
			.orElseGet(List::of));
	}
}
