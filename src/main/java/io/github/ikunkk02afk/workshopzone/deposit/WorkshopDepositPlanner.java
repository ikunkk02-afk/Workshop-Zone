package io.github.ikunkk02afk.workshopzone.deposit;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import net.minecraft.util.math.BlockPos;

import java.util.Comparator;
import java.util.Objects;

public final class WorkshopDepositPlanner {
	private static final Comparator<BlockPos> POSITION_ORDER = Comparator
		.comparingInt(BlockPos::getX)
		.thenComparingInt(BlockPos::getY)
		.thenComparingInt(BlockPos::getZ);

	public static final Comparator<Target> TARGET_ORDER = Comparator
		.comparingInt((Target target) -> target.mode() == ContainerLabelMode.EXACT_ITEM ? 0 : 1)
		.thenComparing(Target::mergeable, Comparator.reverseOrder())
		.thenComparingDouble(Target::distanceSquared)
		.thenComparingInt(Target::scanIndex)
		.thenComparing(Target::representativePosition, POSITION_ORDER);

	private WorkshopDepositPlanner() {
	}

	public static boolean isEligible(ContainerLabelSummary summary) {
		return summary != null
			&& (summary.mode() == ContainerLabelMode.EXACT_ITEM || summary.mode() == ContainerLabelMode.ITEM_TAG)
			&& !summary.conflict()
			&& !summary.unavailable();
	}

	public record Target(
		ContainerLabelMode mode,
		BlockPos representativePosition,
		double distanceSquared,
		int scanIndex,
		boolean mergeable
	) {
		public Target {
			Objects.requireNonNull(mode, "mode");
			representativePosition = Objects.requireNonNull(representativePosition, "representativePosition").toImmutable();
			if (mode == ContainerLabelMode.NONE || !Double.isFinite(distanceSquared) || distanceSquared < 0 || scanIndex < 0) {
				throw new IllegalArgumentException("Invalid workshop deposit target");
			}
		}
	}
}
