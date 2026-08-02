package io.github.ikunkk02afk.workshopzone.mixin;

import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
	@Inject(method = "onHandledScreenClosed", at = @At("HEAD"))
	private void workshopZone$clearSession(CallbackInfo ci) {
		ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
		WorkshopSessionManager.getInstance().clearIfSync(player, player.currentScreenHandler.syncId);
	}
}
