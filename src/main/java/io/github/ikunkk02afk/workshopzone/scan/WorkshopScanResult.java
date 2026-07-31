package io.github.ikunkk02afk.workshopzone.scan;

import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;

public record WorkshopScanResult(
	BlockPos center,
	int horizontalRadius,
	int verticalRadius,
	List<WorkshopBlockEntry> entries,
	int containerCount,
	int processingDeviceCount
) {
	public WorkshopScanResult {
		center = Objects.requireNonNull(center, "center").toImmutable();
		if (horizontalRadius < 0 || verticalRadius < 0) {
			throw new IllegalArgumentException("Scan radii must not be negative");
		}
		entries = List.copyOf(entries);
		long actualContainerCount = entries.stream().filter(WorkshopBlockEntry::container).count();
		long actualProcessingDeviceCount = entries.stream().filter(WorkshopBlockEntry::processingDevice).count();
		if (containerCount != actualContainerCount || processingDeviceCount != actualProcessingDeviceCount) {
			throw new IllegalArgumentException("Result category counts must match its entries");
		}
	}

	public static WorkshopScanResult create(
		BlockPos center,
		int horizontalRadius,
		int verticalRadius,
		List<WorkshopBlockEntry> entries
	) {
		int containers = 0;
		int processingDevices = 0;
		for (WorkshopBlockEntry entry : entries) {
			if (entry.container()) {
				containers++;
			}
			if (entry.processingDevice()) {
				processingDevices++;
			}
		}
		return new WorkshopScanResult(
			center,
			horizontalRadius,
			verticalRadius,
			entries,
			containers,
			processingDevices
		);
	}

	public int size() {
		return entries.size();
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}
}
