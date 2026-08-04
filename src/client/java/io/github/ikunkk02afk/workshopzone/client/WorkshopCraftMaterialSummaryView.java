package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftMaterialSummary;
import net.minecraft.item.ItemStack;

public record WorkshopCraftMaterialSummaryView(ItemStack stack, int totalAmount, int playerAmount, int storageAmount) {
	public static WorkshopCraftMaterialSummaryView from(WorkshopCraftMaterialSummary summary) {
		return new WorkshopCraftMaterialSummaryView(
			summary.stack(), summary.totalAmount(), summary.playerAmount(), summary.storageAmount()
		);
	}

	@Override
	public ItemStack stack() {
		return stack.copy();
	}
}
