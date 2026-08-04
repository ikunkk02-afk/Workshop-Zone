package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopItemCatalogPayload;

public final class WorkshopItemCatalogResultFilter {
	private WorkshopItemCatalogResultFilter() {
	}

	public static boolean matches(
		WorkshopItemCatalogPayload payload,
		long requestId,
		long sessionId,
		long revision,
		int syncId
	) {
		return payload != null
			&& payload.requestId() == requestId
			&& payload.sessionId() == sessionId
			&& payload.revision() == revision
			&& payload.syncId() == syncId;
	}
}
