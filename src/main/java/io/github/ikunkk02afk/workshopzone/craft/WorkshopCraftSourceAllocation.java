package io.github.ikunkk02afk.workshopzone.craft;

public record WorkshopCraftSourceAllocation(
	int supplyId,
	WorkshopCraftSourceKind sourceKind,
	int amount
) {
	public WorkshopCraftSourceAllocation {
		if (supplyId < 0 || sourceKind == null || amount <= 0) {
			throw new IllegalArgumentException("Invalid workshop crafting source allocation");
		}
	}
}
