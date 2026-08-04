package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.network.RequestWorkshopItemCatalogPayload;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSession;
import io.github.ikunkk02afk.workshopzone.session.WorkshopSessionValidation;

public final class WorkshopItemCatalogChecks {
	public static final int COOLDOWN_TICKS = 10;

	private WorkshopItemCatalogChecks() {
	}

	public static WorkshopItemCatalogResultCode validateIdentity(
		WorkshopSession session,
		RequestWorkshopItemCatalogPayload request,
		int currentSyncId,
		boolean dimensionMatches,
		boolean supportedHandler,
		WorkshopSessionValidation validation
	) {
		if (session == null || session.sessionId() != request.sessionId()) {
			return WorkshopItemCatalogResultCode.INVALID_SESSION;
		}
		if (session.revision() != request.revision()) {
			return WorkshopItemCatalogResultCode.STALE_SESSION;
		}
		if (session.syncId() != request.syncId() || session.syncId() != currentSyncId
			|| !dimensionMatches || !supportedHandler || validation != WorkshopSessionValidation.VALID) {
			return WorkshopItemCatalogResultCode.INVALID_SESSION;
		}
		return WorkshopItemCatalogResultCode.SUCCESS;
	}

	public static boolean cooldownElapsed(long currentTick, long previousTick) {
		return currentTick - previousTick >= COOLDOWN_TICKS;
	}
}
