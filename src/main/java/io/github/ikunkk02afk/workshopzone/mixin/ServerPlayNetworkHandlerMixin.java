package io.github.ikunkk02afk.workshopzone.mixin;

import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionManager;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeMatcher;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onCraftRequest", at = @At("HEAD"), cancellable = true)
	private void interceptCraftRequest(CraftRequestC2SPacket packet, CallbackInfo ci) {
		if (this.player == null) return;
		if (!(this.player.currentScreenHandler instanceof CraftingScreenHandler)) return;
		if (packet.shouldCraftAll()) return;

		RecipeEntry<?> entry = this.player.getServer().getRecipeManager().get(packet.getRecipeId()).orElse(null);
		if (entry == null || !(entry.value() instanceof CraftingRecipe)) return;

		RecipeMatcher matcher = new RecipeMatcher();
		this.player.getInventory().populateRecipeFinder(matcher);
		if (matcher.match(entry.value(), null)) return;

		WorkshopSessionManager manager = WorkshopSessionManager.getInstance();
		if (manager.previewCraft(this.player, packet.getSyncId(), packet.getRecipeId(), false)) {
			ci.cancel();
		}
	}
}
