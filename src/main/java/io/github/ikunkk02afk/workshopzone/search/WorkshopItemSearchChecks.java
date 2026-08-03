package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.network.SearchWorkshopItemPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionValidation;

public final class WorkshopItemSearchChecks {
	public static final int COOLDOWN_TICKS = 10;
	public static final double MAX_DISTANCE_SQUARED = 64.0;

	private WorkshopItemSearchChecks() {
	}

	public static WorkshopItemSearchResultCode validateIdentity(
		WorkshopSession session,
		SearchWorkshopItemPayload request,
		int currentSyncId,
		boolean dimensionMatches,
		boolean supportedHandler,
		WorkshopSessionValidation validation
	) {
		if (session == null || session.sessionId() != request.sessionId()) {
			return WorkshopItemSearchResultCode.INVALID_SESSION;
		}
		if (session.revision() != request.revision()) {
			return WorkshopItemSearchResultCode.STALE_SESSION;
		}
		if (session.syncId() != request.syncId() || session.syncId() != currentSyncId
			|| !dimensionMatches || !supportedHandler || validation != WorkshopSessionValidation.VALID) {
			return WorkshopItemSearchResultCode.INVALID_SESSION;
		}
		return WorkshopItemSearchResultCode.SUCCESS;
	}

	public static boolean cooldownElapsed(long currentTick, long previousTick) {
		return currentTick - previousTick >= COOLDOWN_TICKS;
	}

	public static boolean isSearchableContainer(WorkshopBlockType type) {
		return type == WorkshopBlockType.CHEST || type == WorkshopBlockType.TRAPPED_CHEST || type == WorkshopBlockType.BARREL;
	}

	public static boolean isWithinDistance(double distanceSquared) {
		return Double.isFinite(distanceSquared) && distanceSquared <= MAX_DISTANCE_SQUARED;
	}
}
