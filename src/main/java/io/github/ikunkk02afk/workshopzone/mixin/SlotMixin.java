package io.github.ikunkk02afk.workshopzone.mixin;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotMixin {
	@Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
	private void workshopZone$enforceContainerLabel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		Slot self = (Slot)(Object)this;
		if (self.inventory instanceof ContainerLabelInventory labeled && !labeled.workshopZone$canInsert(stack)) {
			cir.setReturnValue(false);
		}
	}
}
