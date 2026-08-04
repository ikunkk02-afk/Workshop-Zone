package io.github.ikunkk02afk.workshopzone.search;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Objects;

public record WorkshopItemSearchContainerResult(
	WorkshopBlockType containerType,
	Identifier blockId,
	BlockPos representativePosition,
	List<BlockPos> highlightPositions,
	long containerItemCount,
	int matchingSlotCount,
	double distanceSquared,
	boolean multipleVariants,
	int scanIndex
) {
	public WorkshopItemSearchContainerResult {
		Objects.requireNonNull(containerType, "containerType");
		Objects.requireNonNull(blockId, "blockId");
		representativePosition = Objects.requireNonNull(representativePosition, "representativePosition").toImmutable();
		highlightPositions = Objects.requireNonNull(highlightPositions, "highlightPositions").stream()
			.map(BlockPos::toImmutable).distinct().toList();
		if (!containerType.isContainer() || highlightPositions.isEmpty() || highlightPositions.size() > 2
			|| !highlightPositions.contains(representativePosition)
			|| containerItemCount <= 0 || matchingSlotCount <= 0
			|| !Double.isFinite(distanceSquared) || distanceSquared < 0 || scanIndex < 0) {
			throw new IllegalArgumentException("Invalid workshop item search container result");
		}
	}

	public WorkshopItemSearchContainerResult(
		BlockPos representativePosition,
		List<BlockPos> highlightPositions,
		long containerItemCount,
		int matchingSlotCount,
		double distanceSquared,
		boolean multipleVariants,
		int scanIndex
	) {
		this(
			WorkshopBlockType.CHEST,
			Identifier.ofVanilla("chest"),
			representativePosition,
			highlightPositions,
			containerItemCount,
			matchingSlotCount,
			distanceSquared,
			multipleVariants,
			scanIndex
		);
	}
}
