package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftMaterialSummary;
import io.github.ikunkk02afk.workshopzone.craft.WorkshopCraftMode;
import io.github.ikunkk02afk.workshopzone.network.WorkshopCraftPreviewPayload;
import net.minecraft.item.ItemStack;

import java.util.List;

public record WorkshopCraftPreviewView(
	WorkshopCraftMode craftMode,
	ItemStack output,
	List<WorkshopCraftMaterialSummaryView> materials,
	int plannedIterations,
	int outputPerIteration,
	long totalOutputCount,
	int playerOnlyMaxIterations,
	int combinedMaxIterations,
	int storageItemCount,
	int usedContainerCount
) {
	public static WorkshopCraftPreviewView from(WorkshopCraftPreviewPayload payload) {
		return new WorkshopCraftPreviewView(
			payload.craftMode(), payload.output(), payload.materials().stream().map(WorkshopCraftMaterialSummaryView::from).toList(),
			payload.plannedIterations(), payload.outputPerIteration(), payload.totalOutputCount(),
			payload.playerOnlyMaxIterations(), payload.combinedMaxIterations(),
			payload.storageItemCount(), payload.usedContainerCount()
		);
	}

	@Override
	public ItemStack output() {
		return output.copy();
	}
}
