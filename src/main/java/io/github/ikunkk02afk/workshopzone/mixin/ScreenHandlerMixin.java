package io.github.ikunkk02afk.workshopzone.mixin;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelFeedback;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenHandler.class)
public abstract class ScreenHandlerMixin {
	@Inject(method = "onSlotClick", at = @At("HEAD"), cancellable = true)
	private void workshopZone$rejectManualInsert(
		int slotIndex,
		int button,
		SlotActionType actionType,
		PlayerEntity player,
		CallbackInfo ci
	) {
		ScreenHandler self = (ScreenHandler)(Object)this;
		if (!(player instanceof ServerPlayerEntity serverPlayer)
			|| slotIndex < 0 || slotIndex >= self.slots.size()) {
			return;
		}
		Slot slot = self.slots.get(slotIndex);
		if (!(slot.inventory instanceof ContainerLabelInventory labeled)) {
			return;
		}
		ItemStack candidate = switch (actionType) {
			case PICKUP, QUICK_CRAFT -> self.getCursorStack();
			case SWAP -> button >= 0 && button < 9 ? player.getInventory().getStack(button) : ItemStack.EMPTY;
			default -> ItemStack.EMPTY;
		};
		if (!candidate.isEmpty() && !labeled.workshopZone$canInsert(candidate)) {
			ContainerLabelFeedback.rejected(serverPlayer, labeled.workshopZone$getLabelSummary());
			ci.cancel();
		}
	}
}
