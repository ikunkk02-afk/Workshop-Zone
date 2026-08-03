package io.github.ikunkk02afk.workshopzone.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

@FunctionalInterface
public interface WorkshopSearchAccessCallback {
	Event<WorkshopSearchAccessCallback> EVENT = EventFactory.createArrayBacked(
		WorkshopSearchAccessCallback.class,
		listeners -> (player, world, representativePosition, targetItem) -> {
			for (WorkshopSearchAccessCallback listener : listeners) {
				if (listener.canSearch(player, world, representativePosition, targetItem) != Result.ALLOW) {
					return Result.DENY;
				}
			}
			return Result.ALLOW;
		}
	);

	Result canSearch(
		ServerPlayerEntity player,
		ServerWorld world,
		BlockPos representativePosition,
		Item targetItem
	);

	enum Result {
		ALLOW,
		DENY
	}
}
