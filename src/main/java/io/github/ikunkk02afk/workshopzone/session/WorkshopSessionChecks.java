package io.github.ikunkk02afk.workshopzone.session;

public final class WorkshopSessionChecks {
	private WorkshopSessionChecks() {
	}

	public static boolean canRefresh(long currentTick, long lastRefreshTick) {
		return currentTick - lastRefreshTick >= WorkshopSessionManager.REFRESH_COOLDOWN_TICKS;
	}

	public static WorkshopSessionValidation validate(
		int expectedSyncId,
		int currentSyncId,
		boolean sameDimension,
		double distanceSquared,
		boolean centerLoaded,
		boolean centerMatches,
		boolean handlerMatches
	) {
		if (expectedSyncId != currentSyncId) {
			return WorkshopSessionValidation.SYNC_MISMATCH;
		}
		if (!sameDimension) {
			return WorkshopSessionValidation.DIMENSION_MISMATCH;
		}
		if (!Double.isFinite(distanceSquared) || distanceSquared > WorkshopSessionManager.MAX_CENTER_DISTANCE_SQUARED) {
			return WorkshopSessionValidation.OUT_OF_RANGE;
		}
		if (!centerLoaded) {
			return WorkshopSessionValidation.CENTER_UNLOADED;
		}
		if (!centerMatches) {
			return WorkshopSessionValidation.CENTER_CHANGED;
		}
		return handlerMatches ? WorkshopSessionValidation.VALID : WorkshopSessionValidation.HANDLER_MISMATCH;
	}
}
