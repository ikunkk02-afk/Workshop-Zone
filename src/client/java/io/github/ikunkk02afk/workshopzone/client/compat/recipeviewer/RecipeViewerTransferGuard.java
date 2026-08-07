package io.github.ikunkk02afk.workshopzone.client.compat.recipeviewer;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class RecipeViewerTransferGuard {
	public static final int DUPLICATE_WINDOW_TICKS = 5;

	private final Map<RequestKey, Long> recentRequests = new HashMap<>();
	private Object screenIdentity;
	private int syncId = -1;
	private long latestTick = Long.MIN_VALUE;

	public synchronized boolean allow(
		RecipeViewerSource source,
		Identifier recipeId,
		int syncId,
		boolean batch,
		Object screenIdentity,
		long currentTick
	) {
		if (this.screenIdentity != screenIdentity || this.syncId != syncId || currentTick < latestTick) {
			recentRequests.clear();
			this.screenIdentity = screenIdentity;
			this.syncId = syncId;
		}
		latestTick = currentTick;
		recentRequests.entrySet().removeIf(entry -> currentTick - entry.getValue() >= DUPLICATE_WINDOW_TICKS);

		RequestKey key = new RequestKey(source, recipeId, syncId, batch);
		Long previousTick = recentRequests.get(key);
		if (previousTick != null && currentTick - previousTick < DUPLICATE_WINDOW_TICKS) {
			return false;
		}
		recentRequests.put(key, currentTick);
		return true;
	}

	public synchronized void clear() {
		recentRequests.clear();
		screenIdentity = null;
		syncId = -1;
		latestTick = Long.MIN_VALUE;
	}

	private record RequestKey(RecipeViewerSource source, Identifier recipeId, int syncId, boolean batch) {
	}
}
