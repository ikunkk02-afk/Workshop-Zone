package io.github.ikunkk02afk.workshopzone.mixin;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelInventory;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelService;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DoubleInventory.class)
public abstract class DoubleInventoryMixin implements ContainerLabelInventory {
	@Shadow @Final private Inventory first;
	@Shadow @Final private Inventory second;

	@Override
	public boolean workshopZone$canInsert(ItemStack stack) {
		ContainerLabelSummary summary = workshopZone$getLabelSummary();
		return stack.isEmpty() || (!summary.blocksInput()
			&& (!(first instanceof ContainerLabelInventory aware) || aware.workshopZone$canInsert(stack))
			&& (!(second instanceof ContainerLabelInventory aware) || aware.workshopZone$canInsert(stack)));
	}

	@Override
	public ContainerLabelSummary workshopZone$getLabelSummary() {
		return ContainerLabelService.summarizeInventories(first, second);
	}
}
