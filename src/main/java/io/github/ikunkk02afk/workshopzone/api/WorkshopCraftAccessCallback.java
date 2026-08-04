package io.github.ikunkk02afk.workshopzone.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

@FunctionalInterface
public interface WorkshopCraftAccessCallback {
	Event<WorkshopCraftAccessCallback> EVENT = EventFactory.createArrayBacked(
		WorkshopCraftAccessCallback.class,
		listeners -> (player, world, representativePosition, recipe, variant, amount) -> {
			for (WorkshopCraftAccessCallback listener : listeners) {
				if (listener.canExtract(player, world, representativePosition, recipe, variant, amount) != Result.ALLOW) {
					return Result.DENY;
				}
			}
			return Result.ALLOW;
		}
	);

	Result canExtract(
		ServerPlayerEntity player,
		ServerWorld world,
		BlockPos representativePosition,
		RecipeEntry<CraftingRecipe> recipe,
		ItemVariant variant,
		long amount
	);

	enum Result {
		ALLOW,
		DENY
	}
}
