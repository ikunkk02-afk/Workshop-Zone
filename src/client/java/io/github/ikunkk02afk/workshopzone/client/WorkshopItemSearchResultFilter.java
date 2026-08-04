package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopItemSearchResultPayload;
import net.minecraft.util.Identifier;

public final class WorkshopItemSearchResultFilter {
	private WorkshopItemSearchResultFilter() {
	}

	public static boolean matches(
		WorkshopItemSearchResultPayload payload,
		long pendingRequestId,
		long sessionId,
		long revision,
		int syncId,
		Identifier targetItemId
	) {
		return payload != null
			&& payload.requestId() == pendingRequestId
			&& payload.sessionId() == sessionId
			&& payload.revision() == revision
			&& payload.syncId() == syncId
			&& payload.targetItemId().equals(targetItemId);
	}
}
