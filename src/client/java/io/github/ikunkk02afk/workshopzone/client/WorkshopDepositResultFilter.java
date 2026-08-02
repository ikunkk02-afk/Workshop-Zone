package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopDepositResultPayload;

public final class WorkshopDepositResultFilter {
	private WorkshopDepositResultFilter() {
	}

	public static boolean matches(
		WorkshopDepositResultPayload payload,
		long pendingRequestId,
		long currentSessionId,
		int currentSyncId
	) {
		return payload != null
			&& payload.requestId() == pendingRequestId
			&& payload.sessionId() == currentSessionId
			&& payload.syncId() == currentSyncId;
	}
}
