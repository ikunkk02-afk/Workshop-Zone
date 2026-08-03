package io.github.ikunkk02afk.workshopzone.client;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;

public record WorkshopContainerHighlight(
	BlockPos representativePosition,
	List<BlockPos> positions,
	Identifier targetItemId,
	long containerItemCount,
	long startTimeMillis,
	long endTimeMillis,
	boolean selected
) {
	public WorkshopContainerHighlight {
		representativePosition = Objects.requireNonNull(representativePosition, "representativePosition").toImmutable();
		positions = Objects.requireNonNull(positions, "positions").stream().map(BlockPos::toImmutable).distinct().toList();
		Objects.requireNonNull(targetItemId, "targetItemId");
		if (positions.isEmpty() || positions.size() > 2 || !positions.contains(representativePosition)
			|| containerItemCount <= 0 || endTimeMillis <= startTimeMillis) {
			throw new IllegalArgumentException("Invalid workshop container highlight");
		}
	}

	public boolean active(long nowMillis) {
		return nowMillis >= startTimeMillis && nowMillis < endTimeMillis;
	}
}
