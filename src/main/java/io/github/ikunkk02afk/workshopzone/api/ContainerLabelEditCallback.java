package io.github.ikunkk02afk.workshopzone.api;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

@FunctionalInterface
public interface ContainerLabelEditCallback {
	Event<ContainerLabelEditCallback> EVENT = EventFactory.createArrayBacked(
		ContainerLabelEditCallback.class,
		listeners -> (player, world, position, currentRule, requestedRule) -> {
			for (ContainerLabelEditCallback listener : listeners) {
				if (!listener.canEdit(player, world, position, currentRule, requestedRule)) {
					return false;
				}
			}
			return true;
		}
	);

	boolean canEdit(
		ServerPlayerEntity player,
		ServerWorld world,
		BlockPos representativePosition,
		ContainerLabelRule currentRule,
		ContainerLabelRule requestedRule
	);
}
