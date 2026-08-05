package io.github.ikunkk02afk.workshopzone.mixin.client;

import io.github.ikunkk02afk.workshopzone.client.ClientWorkshopCraftState;
import io.github.ikunkk02afk.workshopzone.client.WorkshopCraftConfirmationOverlay;
import io.github.ikunkk02afk.workshopzone.client.WorkshopCraftInputOverlayRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingScreen.class)
public abstract class CraftingConfirmationInputMixin {
	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void blockClicksDuringConfirmation(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
		WorkshopCraftConfirmationOverlay overlay = WorkshopCraftInputOverlayRegistry.current();
		if (overlay != null && overlay.mouseClicked(mouseX, mouseY, button)) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void blockKeysDuringConfirmation(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (ClientWorkshopCraftState.isVisible()) {
			if (keyCode == 256) {
				ClientWorkshopCraftState.cancel(MinecraftClient.getInstance());
			}
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
	private void blockTypedCharactersDuringConfirmation(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		if (ClientWorkshopCraftState.isVisible()) {
			cir.setReturnValue(true);
		}
	}
}
