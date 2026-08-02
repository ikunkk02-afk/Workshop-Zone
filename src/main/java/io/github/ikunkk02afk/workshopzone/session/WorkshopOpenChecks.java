package io.github.ikunkk02afk.workshopzone.session;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

public final class WorkshopOpenChecks {
	private WorkshopOpenChecks() {
	}

	public static WorkshopOpenResult validateIdentity(
		WorkshopSession session,
		long sessionId,
		long revision,
		int syncId,
		int currentSyncId
	) {
		if (session == null || session.sessionId() != sessionId) {
			return WorkshopOpenResult.INVALID_SESSION;
		}
		if (session.revision() != revision) {
			return WorkshopOpenResult.STALE_SNAPSHOT;
		}
		return session.syncId() == syncId && session.syncId() == currentSyncId
			? WorkshopOpenResult.SUCCESS
			: WorkshopOpenResult.INVALID_SESSION;
	}

	public static Optional<WorkshopBlockEntry> findTarget(List<WorkshopBlockEntry> entries, BlockPos targetPos) {
		return entries.stream().filter(entry -> entry.position().equals(targetPos)).findFirst();
	}

	public static boolean isWithinRemoteOpenDistance(double distanceSquared) {
		return Double.isFinite(distanceSquared)
			&& distanceSquared <= WorkshopSessionManager.MAX_REMOTE_OPEN_DISTANCE_SQUARED;
	}

	public static boolean cooldownElapsed(long currentTick, long previousRequestTick) {
		return currentTick - previousRequestTick >= WorkshopSessionManager.OPEN_COOLDOWN_TICKS;
	}
}
