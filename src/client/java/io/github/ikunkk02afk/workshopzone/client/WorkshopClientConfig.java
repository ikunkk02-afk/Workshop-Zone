package io.github.ikunkk02afk.workshopzone.client;

public record WorkshopClientConfig(
	int version,
	WorkshopSidebarPosition sidebarPosition,
	boolean autoAvoidRecipeViewers,
	double customX,
	double customY
) {
	public static final int CURRENT_VERSION = 1;
	public static final double DEFAULT_CUSTOM_X = 0.5;
	public static final double DEFAULT_CUSTOM_Y = 0.1;

	public WorkshopClientConfig {
		version = CURRENT_VERSION;
		sidebarPosition = sidebarPosition == null ? WorkshopSidebarPosition.AUTO : sidebarPosition;
		customX = sanitizeCoordinate(customX, DEFAULT_CUSTOM_X);
		customY = sanitizeCoordinate(customY, DEFAULT_CUSTOM_Y);
	}

	public static WorkshopClientConfig defaults() {
		return new WorkshopClientConfig(
			CURRENT_VERSION,
			WorkshopSidebarPosition.AUTO,
			true,
			DEFAULT_CUSTOM_X,
			DEFAULT_CUSTOM_Y
		);
	}

	public WorkshopClientConfig withPosition(WorkshopSidebarPosition position) {
		return new WorkshopClientConfig(version, position, autoAvoidRecipeViewers, customX, customY);
	}

	public WorkshopClientConfig withCustomPosition(double x, double y) {
		return new WorkshopClientConfig(version, WorkshopSidebarPosition.CUSTOM, autoAvoidRecipeViewers, x, y);
	}

	public WorkshopClientConfig reset() {
		return defaults();
	}

	private static double sanitizeCoordinate(double value, double fallback) {
		if (!Double.isFinite(value)) {
			return fallback;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}
}
