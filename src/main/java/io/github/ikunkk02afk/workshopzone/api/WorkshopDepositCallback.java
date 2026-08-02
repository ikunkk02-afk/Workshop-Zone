package io.github.ikunkk02afk.workshopzone.api;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

@FunctionalInterface
public interface WorkshopDepositCallback {
	Event<WorkshopDepositCallback> EVENT = EventFactory.createArrayBacked(
		WorkshopDepositCallback.class,
		listeners -> (player, world, position, rule, variant) -> {
			for (WorkshopDepositCallback listener : listeners) {
				if (listener.canDeposit(player, world, position, rule, variant) == Result.DENY) {
					return Result.DENY;
				}
			}
			return Result.ALLOW;
		}
	);

	Result canDeposit(
		ServerPlayerEntity player,
		ServerWorld world,
		BlockPos representativePosition,
		ContainerLabelRule rule,
		ItemVariant variant
	);

	enum Result {
		ALLOW,
		DENY
	}
}
