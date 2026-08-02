package io.github.ikunkk02afk.workshopzone.scan;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Objects;

public record WorkshopBlockEntry(
	WorkshopBlockType type,
	BlockPos position,
	Identifier blockId,
	double distanceSquared,
	boolean container,
	boolean processingDevice,
	ContainerLabelSummary labelSummary
) {
	public WorkshopBlockEntry {
		Objects.requireNonNull(type, "type");
		position = Objects.requireNonNull(position, "position").toImmutable();
		Objects.requireNonNull(blockId, "blockId");
		Objects.requireNonNull(labelSummary, "labelSummary");
		if (distanceSquared < 0.0) {
			throw new IllegalArgumentException("distanceSquared must not be negative");
		}
		if (container != type.isContainer() || processingDevice != type.isProcessingDevice()) {
			throw new IllegalArgumentException("Entry category flags must match its block type");
		}
	}

	public static WorkshopBlockEntry create(
		WorkshopBlockType type,
		BlockPos position,
		Identifier blockId,
		double distanceSquared
	) {
		return new WorkshopBlockEntry(
			type,
			position,
			blockId,
			distanceSquared,
			type.isContainer(),
			type.isProcessingDevice(),
			ContainerLabelSummary.NONE
		);
	}

	public WorkshopBlockEntry withLabelSummary(ContainerLabelSummary summary) {
		return new WorkshopBlockEntry(type, position, blockId, distanceSquared, container, processingDevice, summary);
	}
}
