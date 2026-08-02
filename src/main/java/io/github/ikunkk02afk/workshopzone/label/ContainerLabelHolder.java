package io.github.ikunkk02afk.workshopzone.label;

import net.minecraft.item.ItemStack;

public interface ContainerLabelHolder extends ContainerLabelInventory {
	ContainerLabelRule workshopZone$getLabelRule();

	void workshopZone$setLabelRule(ContainerLabelRule rule);

	@Override
	default boolean workshopZone$canInsert(ItemStack stack) {
		return ContainerLabelService.canInsert(this, stack);
	}

	@Override
	default ContainerLabelSummary workshopZone$getLabelSummary() {
		return ContainerLabelService.summary(this);
	}
}
