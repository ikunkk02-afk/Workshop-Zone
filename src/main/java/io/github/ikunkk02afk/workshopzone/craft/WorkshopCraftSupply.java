package io.github.ikunkk02afk.workshopzone.craft;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;

import java.util.Objects;

public record WorkshopCraftSupply(
	int id,
	WorkshopCraftSourceKind sourceKind,
	ItemVariant variant,
	int amount,
	int stableOrder
) {
	public WorkshopCraftSupply {
		if (id < 0 || amount <= 0 || stableOrder < 0) {
			throw new IllegalArgumentException("Invalid workshop crafting supply");
		}
		Objects.requireNonNull(sourceKind, "sourceKind");
		Objects.requireNonNull(variant, "variant");
		if (variant.isBlank()) {
			throw new IllegalArgumentException("Workshop crafting supply cannot be blank");
		}
	}
}
