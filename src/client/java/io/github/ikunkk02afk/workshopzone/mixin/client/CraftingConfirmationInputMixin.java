package io.github.ikunkk02afk.workshopzone.mixin.client;

import io.github.ikunkk02afk.workshopzone.client.ClientWorkshopCraftOverlay;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingScreen.class)
public abstract class CraftingConfirmationInputMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void blockClicksDuringConfirmation(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		if (ClientWorkshopCraftOverlay.consumeOverlayClick(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void blockKeysDuringConfirmation(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (ClientWorkshopCraftOverlay.consumeOverlayKey(keyCode, scanCode, modifiers)) {
			cir.setReturnValue(true);
		}
	}
}
