package io.github.ikunkk02afk.workshopzone.craft;

public record WorkshopCraftPlanBuildResult(WorkshopCraftPlanStatus status, WorkshopCraftPlan plan) {
	public WorkshopCraftPlanBuildResult {
		if ((status == WorkshopCraftPlanStatus.AVAILABLE) != (plan != null)) {
			throw new IllegalArgumentException("Workshop crafting plan status and plan disagree");
		}
	}

	public static WorkshopCraftPlanBuildResult available(WorkshopCraftPlan plan) {
		return new WorkshopCraftPlanBuildResult(WorkshopCraftPlanStatus.AVAILABLE, plan);
	}

	public static WorkshopCraftPlanBuildResult failure(WorkshopCraftPlanStatus status) {
		return new WorkshopCraftPlanBuildResult(status, null);
	}
}
