package io.github.ikunkk02afk.workshopzone.craft;

import java.util.List;
import java.util.Map;

public record WorkshopCraftPlan(
	WorkshopCraftParsedRecipe recipe,
	WorkshopCraftMode craftMode,
	int plannedIterations,
	int playerOnlyMaxIterations,
	int combinedMaxIterations,
	List<WorkshopCraftAssignment> assignments,
	List<WorkshopCraftSourceAllocation> sourceAllocations,
	List<WorkshopCraftGridPlacement> placements,
	Map<Integer, WorkshopCraftLiveSupply> liveSupplies,
	List<WorkshopCraftMaterialSummary> materialSummaries,
	int playerItemCount,
	int storageItemCount,
	int usedContainerCount
) {
	public WorkshopCraftPlan {
		assignments = List.copyOf(assignments);
		sourceAllocations = List.copyOf(sourceAllocations);
		placements = List.copyOf(placements);
		liveSupplies = Map.copyOf(liveSupplies);
		materialSummaries = List.copyOf(materialSummaries);
		long allocatedItems = sourceAllocations.stream().mapToLong(WorkshopCraftSourceAllocation::amount).sum();
		long summarizedItems = materialSummaries.stream().mapToLong(WorkshopCraftMaterialSummary::totalAmount).sum();
		boolean invalidAllocation = false;
		for (WorkshopCraftSourceAllocation allocation : sourceAllocations) {
			WorkshopCraftLiveSupply live = liveSupplies.get(allocation.supplyId());
			if (live == null || live.supply().sourceKind() != allocation.sourceKind()) {
				invalidAllocation = true;
				break;
			}
		}
		if (craftMode == null || plannedIterations <= 0 || plannedIterations > 64
			|| craftMode == WorkshopCraftMode.SINGLE && plannedIterations != 1
			|| craftMode == WorkshopCraftMode.BATCH && plannedIterations <= 1
			|| playerOnlyMaxIterations < 0 || playerOnlyMaxIterations > combinedMaxIterations
			|| combinedMaxIterations < plannedIterations || combinedMaxIterations > 64
			|| assignments.isEmpty() || assignments.size() > 9 || placements.size() != assignments.size()
			|| sourceAllocations.isEmpty()
			|| playerItemCount < 0 || storageItemCount < 0
			|| playerItemCount + storageItemCount != assignments.size() * plannedIterations
			|| allocatedItems != playerItemCount + storageItemCount
			|| summarizedItems != allocatedItems || invalidAllocation
			|| usedContainerCount < 0) {
			throw new IllegalArgumentException("Invalid workshop crafting plan");
		}
	}
}
