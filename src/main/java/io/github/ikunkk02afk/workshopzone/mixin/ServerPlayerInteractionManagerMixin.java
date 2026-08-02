package io.github.ikunkk02afk.workshopzone.mixin;

import io.github.ikunkk02afk.workshopzone.WorkshopZone;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockCatalog;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
	@Unique
	private ScreenHandler workshopZone$previousHandler;

	@Inject(method = "interactBlock", at = @At("HEAD"))
	private void workshopZone$captureHandler(
		ServerPlayerEntity player,
		World world,
		ItemStack stack,
		Hand hand,
		BlockHitResult hitResult,
		CallbackInfoReturnable<ActionResult> cir
	) {
		workshopZone$previousHandler = player.currentScreenHandler;
	}

	@Inject(method = "interactBlock", at = @At("RETURN"))
	private void workshopZone$confirmOpenedBlock(
		ServerPlayerEntity player,
		World world,
		ItemStack stack,
		Hand hand,
		BlockHitResult hitResult,
		CallbackInfoReturnable<ActionResult> cir
	) {
		try {
			if (!cir.getReturnValue().isAccepted()
				|| player.currentScreenHandler == player.playerScreenHandler
				|| player.currentScreenHandler == workshopZone$previousHandler) {
				return;
			}
			WorkshopBlockType type = WorkshopBlockCatalog.vanilla()
				.find(world.getBlockState(hitResult.getBlockPos()).getBlock()).orElse(null);
			if (type != null && WorkshopSessionManager.matchesHandler(type, player.currentScreenHandler)) {
				WorkshopZone.LOGGER.debug(
					"Confirmed supported block screen for player {} at {} type {} syncId {}",
					player.getGameProfile().getName(), hitResult.getBlockPos(), type, player.currentScreenHandler.syncId
				);
				WorkshopSessionManager.getInstance().open(player, hitResult.getBlockPos(), type);
			}
		} finally {
			workshopZone$previousHandler = null;
		}
	}
}
