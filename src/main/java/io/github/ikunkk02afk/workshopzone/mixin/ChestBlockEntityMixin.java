package io.github.ikunkk02afk.workshopzone.mixin;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelData;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelHolder;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelRule;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChestBlockEntity.class)
public abstract class ChestBlockEntityMixin implements ContainerLabelHolder {
	@Unique
	private ContainerLabelRule workshopZone$labelRule = ContainerLabelRule.NONE;

	@Inject(method = "readNbt", at = @At("TAIL"))
	private void workshopZone$readLabel(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfo ci) {
		workshopZone$labelRule = ContainerLabelData.read(nbt);
	}

	@Inject(method = "writeNbt", at = @At("TAIL"))
	private void workshopZone$writeLabel(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup, CallbackInfo ci) {
		ContainerLabelData.write(nbt, workshopZone$labelRule);
	}

	@Override
	public ContainerLabelRule workshopZone$getLabelRule() {
		return workshopZone$labelRule;
	}

	@Override
	public void workshopZone$setLabelRule(ContainerLabelRule rule) {
		BlockEntity self = (BlockEntity)(Object)this;
		if (self.getWorld() != null && self.getWorld().isClient) {
			return;
		}
		workshopZone$labelRule = rule;
		self.markDirty();
	}

	public boolean isValid(int slot, ItemStack stack) {
		return workshopZone$canInsert(stack);
	}
}
