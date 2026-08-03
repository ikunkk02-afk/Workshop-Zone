package io.github.ikunkk02afk.workshopzone.search;

import net.minecraft.util.math.BlockPos;

import java.util.Comparator;

public final class WorkshopItemSearchPlanner {
	public static final Comparator<WorkshopItemSearchContainerResult> RESULT_ORDER = Comparator
		.comparingDouble(WorkshopItemSearchContainerResult::distanceSquared)
		.thenComparing(Comparator.comparingLong(WorkshopItemSearchContainerResult::containerItemCount).reversed())
		.thenComparingInt(WorkshopItemSearchContainerResult::scanIndex)
		.thenComparingInt(result -> result.representativePosition().getX())
		.thenComparingInt(result -> result.representativePosition().getY())
		.thenComparingInt(result -> result.representativePosition().getZ());

	private WorkshopItemSearchPlanner() {
	}
}
