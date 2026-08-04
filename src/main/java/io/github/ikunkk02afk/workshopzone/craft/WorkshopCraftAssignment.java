package io.github.ikunkk02afk.workshopzone.craft;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;

import java.util.Objects;

public record WorkshopCraftAssignment(
	int ingredientIndex,
	int supplyId,
	WorkshopCraftSourceKind sourceKind,
	ItemVariant variant
) {
	public WorkshopCraftAssignment {
		if (ingredientIndex < 0 || ingredientIndex >= 9 || supplyId < 0) {
			throw new IllegalArgumentException("Invalid workshop crafting assignment");
		}
		Objects.requireNonNull(sourceKind, "sourceKind");
		Objects.requireNonNull(variant, "variant");
		if (variant.isBlank()) {
			throw new IllegalArgumentException("Workshop crafting assignment cannot use a blank variant");
		}
	}
}
