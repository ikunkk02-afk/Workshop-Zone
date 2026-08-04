package io.github.ikunkk02afk.workshopzone.craft;

import java.util.Objects;
import java.util.UUID;

public final class WorkshopCraftPendingChecks {
	private WorkshopCraftPendingChecks() {
	}

	public static WorkshopCraftPendingValidation validate(
		WorkshopCraftPendingConfirmation pending,
		long previewId,
		UUID playerId,
		long sessionId,
		long revision,
		int syncId,
		long currentTick
	) {
		Objects.requireNonNull(playerId, "playerId");
		if (pending == null || pending.previewId() != previewId || !pending.playerId().equals(playerId)
			|| pending.syncId() != syncId) {
			return WorkshopCraftPendingValidation.INVALID_CONFIRMATION;
		}
		if (currentTick >= pending.expiresAtTick()) {
			return WorkshopCraftPendingValidation.EXPIRED;
		}
		if (pending.sessionId() != sessionId || pending.revision() != revision) {
			return WorkshopCraftPendingValidation.STALE_SESSION;
		}
		return WorkshopCraftPendingValidation.VALID;
	}
}
