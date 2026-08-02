package io.github.ikunkk02afk.workshopzone.session;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopScanResult;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Objects;
import java.util.UUID;

public record WorkshopSession(
	long sessionId,
	long revision,
	UUID playerId,
	RegistryKey<World> dimension,
	BlockPos scanCenter,
	BlockPos openedEntryPosition,
	WorkshopBlockType openedBlockType,
	int syncId,
	long createdAt,
	long lastRefreshAt,
	WorkshopScanResult scanResult
) {
	public WorkshopSession {
		Objects.requireNonNull(playerId, "playerId");
		Objects.requireNonNull(dimension, "dimension");
		scanCenter = Objects.requireNonNull(scanCenter, "scanCenter").toImmutable();
		openedEntryPosition = Objects.requireNonNull(openedEntryPosition, "openedEntryPosition").toImmutable();
		Objects.requireNonNull(openedBlockType, "openedBlockType");
		Objects.requireNonNull(scanResult, "scanResult");
		if (sessionId < 0 || revision < 0 || syncId < 0) {
			throw new IllegalArgumentException("Session ids must be non-negative");
		}
	}

	public WorkshopSession refreshed(long refreshAt, WorkshopScanResult result) {
		return new WorkshopSession(
			sessionId, revision + 1, playerId, dimension, scanCenter, openedEntryPosition, openedBlockType,
			syncId, createdAt, refreshAt, result
		);
	}

	public WorkshopSession labelEdited(WorkshopScanResult result) {
		return new WorkshopSession(
			sessionId, revision + 1, playerId, dimension, scanCenter, openedEntryPosition, openedBlockType,
			syncId, createdAt, lastRefreshAt, result
		);
	}
}
