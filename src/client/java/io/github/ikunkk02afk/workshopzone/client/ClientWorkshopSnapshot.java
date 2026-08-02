package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public record ClientWorkshopSnapshot(
	long sessionId,
	long revision,
	int syncId,
	Identifier dimensionId,
	BlockPos center,
	WorkshopBlockType openedBlockType,
	int totalEntryCount,
	int containerCount,
	int workstationCount,
	boolean truncated,
	List<ClientWorkshopEntry> entries
) {
	public ClientWorkshopSnapshot {
		center = center.toImmutable();
		entries = List.copyOf(entries);
	}
}
