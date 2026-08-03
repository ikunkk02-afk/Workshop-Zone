package io.github.ikunkk02afk.workshopzone.client;

public record WorkshopSidebarPlacement(
	WorkshopSidebarPosition requestedPosition,
	WorkshopSidebarPosition resolvedPosition,
	WorkshopSidebarMetrics.Rect panel,
	boolean collapsed,
	boolean fallbackUsed,
	boolean constrained,
	WorkshopSidebarMetrics.Rect dragArea
) {
	public WorkshopSidebarMetrics.Rect exclusionArea() {
		return panel;
	}
}
