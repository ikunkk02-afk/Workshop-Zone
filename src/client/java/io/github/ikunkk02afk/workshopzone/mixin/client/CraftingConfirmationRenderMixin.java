package io.github.ikunkk02afk.workshopzone.mixin.client;

import io.github.ikunkk02afk.workshopzone.client.WorkshopCraftConfirmationOverlay;
import io.github.ikunkk02afk.workshopzone.client.WorkshopCraftInputOverlayRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CraftingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingScreen.class)
public abstract class CraftingConfirmationRenderMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void renderConfirmationOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		WorkshopCraftConfirmationOverlay overlay = WorkshopCraftInputOverlayRegistry.current();
		if (overlay != null) {
			overlay.render(context, mouseX, mouseY, delta);
		}
	}
}
