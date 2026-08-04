package io.github.ikunkk02afk.workshopzone.craft;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WorkshopCraftPendingStore {
	private final Map<UUID, WorkshopCraftPendingConfirmation> pendingByPlayer = new HashMap<>();

	public Optional<WorkshopCraftPendingConfirmation> put(WorkshopCraftPendingConfirmation pending) {
		return Optional.ofNullable(pendingByPlayer.put(pending.playerId(), pending));
	}

	public Optional<WorkshopCraftPendingConfirmation> get(UUID playerId) {
		return Optional.ofNullable(pendingByPlayer.get(playerId));
	}

	public Optional<WorkshopCraftPendingConfirmation> consume(UUID playerId, long previewId) {
		WorkshopCraftPendingConfirmation pending = pendingByPlayer.get(playerId);
		if (pending == null || pending.previewId() != previewId) {
			return Optional.empty();
		}
		pendingByPlayer.remove(playerId);
		return Optional.of(pending);
	}

	public void clear(UUID playerId) {
		pendingByPlayer.remove(playerId);
	}

	public void clearAll() {
		pendingByPlayer.clear();
	}

	public void clearExpired(long currentTick) {
		pendingByPlayer.values().removeIf(pending -> currentTick >= pending.expiresAtTick());
	}

	public int size() {
		return pendingByPlayer.size();
	}
}
