package io.github.ikunkk02afk.workshopzone.craft;

import java.util.List;
import java.util.Map;

public record WorkshopCraftPlan(
	WorkshopCraftParsedRecipe recipe,
	List<WorkshopCraftAssignment> assignments,
	List<WorkshopCraftGridPlacement> placements,
	Map<Integer, WorkshopCraftLiveSupply> liveSupplies,
	List<WorkshopCraftMaterialSummary> materialSummaries,
	int playerItemCount,
	int storageItemCount,
	int usedContainerCount
) {
	public WorkshopCraftPlan {
		assignments = List.copyOf(assignments);
		placements = List.copyOf(placements);
		liveSupplies = Map.copyOf(liveSupplies);
		materialSummaries = List.copyOf(materialSummaries);
		if (assignments.isEmpty() || assignments.size() > 9 || placements.size() != assignments.size()
			|| playerItemCount < 0 || storageItemCount < 0 || playerItemCount + storageItemCount != assignments.size()
			|| usedContainerCount < 0) {
			throw new IllegalArgumentException("Invalid workshop crafting plan");
		}
	}
}
