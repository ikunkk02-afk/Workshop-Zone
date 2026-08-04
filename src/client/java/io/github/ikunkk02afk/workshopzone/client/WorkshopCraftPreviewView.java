package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftMaterialSummary;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftPreviewPayload;
import net.minecraft.item.ItemStack;

import java.util.List;

public record WorkshopCraftPreviewView(
	ItemStack output,
	List<WorkshopCraftMaterialSummaryView> materials,
	int storageItemCount,
	int usedContainerCount
) {
	public static WorkshopCraftPreviewView from(WorkshopCraftPreviewPayload payload) {
		return new WorkshopCraftPreviewView(
			payload.output(), payload.materials().stream().map(WorkshopCraftMaterialSummaryView::from).toList(),
			payload.storageItemCount(), payload.usedContainerCount()
		);
	}

	@Override
	public ItemStack output() {
		return output.copy();
	}
}
