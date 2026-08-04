package io.github.ikunkk02afk.workshopzone.craft;

import net.minecraft.item.ItemStack;

import java.util.Objects;

public record WorkshopCraftMaterialSummary(
	ItemStack stack,
	int totalAmount,
	int playerAmount,
	int storageAmount
) {
	public WorkshopCraftMaterialSummary {
		Objects.requireNonNull(stack, "stack");
		if (stack.isEmpty() || totalAmount <= 0 || playerAmount < 0 || storageAmount < 0
			|| playerAmount + storageAmount != totalAmount) {
			throw new IllegalArgumentException("Invalid workshop crafting material summary");
		}
		stack = stack.copyWithCount(1);
	}

	@Override
	public ItemStack stack() {
		return stack.copy();
	}
}
