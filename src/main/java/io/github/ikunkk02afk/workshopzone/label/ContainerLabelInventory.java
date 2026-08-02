package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.item.ItemStack;

public interface ContainerLabelInventory {
	boolean workshopZone$canInsert(ItemStack stack);

	ContainerLabelSummary workshopZone$getLabelSummary();
}
