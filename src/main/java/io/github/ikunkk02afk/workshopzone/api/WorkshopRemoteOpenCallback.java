package io.github.ikunkk02afk.workshopzone.api;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

@FunctionalInterface
public interface WorkshopRemoteOpenCallback {
	Event<WorkshopRemoteOpenCallback> EVENT = EventFactory.createArrayBacked(
		WorkshopRemoteOpenCallback.class,
		listeners -> (player, world, targetPos, targetState, targetType) -> {
			for (WorkshopRemoteOpenCallback listener : listeners) {
				if (!listener.canOpen(player, world, targetPos, targetState, targetType)) {
					return false;
				}
			}
			return true;
		}
	);

	boolean canOpen(
		ServerPlayerEntity player,
		ServerWorld world,
		BlockPos targetPos,
		BlockState targetState,
		WorkshopBlockType targetType
	);
}
