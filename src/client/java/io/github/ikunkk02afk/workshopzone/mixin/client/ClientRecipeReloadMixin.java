package io.github.ikunkk02afk.workshopzone.mixin.client;

import io.github.ikunkk02afk.workshopzone.client.ClientWorkshopCraftState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientRecipeReloadMixin {
	@Inject(method = "onSynchronizeRecipes", at = @At("TAIL"))
	private void clearCraftStateOnRecipeReload(CallbackInfo ci) {
		ClientWorkshopCraftState.reset();
	}
}
