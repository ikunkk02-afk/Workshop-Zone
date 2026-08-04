package io.github.ikunkk02afk.workshopzone.craft;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;

public record WorkshopCraftGridPlacement(
	int handlerSlotIndex,
	int ingredientIndex,
	int supplyId,
	WorkshopCraftSourceKind sourceKind,
	ItemVariant variant
) {
	public WorkshopCraftGridPlacement {
		if (handlerSlotIndex < 1 || handlerSlotIndex > 9) {
			throw new IllegalArgumentException("Workshop crafting target must be a 3x3 input slot");
		}
	}
}
