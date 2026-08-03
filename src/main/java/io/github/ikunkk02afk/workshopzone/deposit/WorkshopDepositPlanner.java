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
		.comparingInt((Target target) -> target.matchKind().priority())
		.thenComparing(Target::mergeable, Comparator.reverseOrder())
		.thenComparingDouble(Target::distanceSquared)
		.thenComparingInt(Target::scanIndex)
		.thenComparing(Target::representativePosition, POSITION_ORDER);

	private WorkshopDepositPlanner() {
	}

	public static boolean isEligible(ContainerLabelSummary summary) {
		return summary != null
			&& (summary.mode() == ContainerLabelMode.EXACT_ITEM
				|| summary.mode() == ContainerLabelMode.ITEM_TAG
				|| summary.mode() == ContainerLabelMode.WHITELIST)
			&& !summary.conflict()
			&& !summary.unavailable();
	}

	public record Target(
		WorkshopDepositMatchKind matchKind,
		BlockPos representativePosition,
		double distanceSquared,
		int scanIndex,
		boolean mergeable
	) {
		public Target(ContainerLabelMode mode, BlockPos position, double distanceSquared, int scanIndex, boolean mergeable) {
			this(
				mode == ContainerLabelMode.EXACT_ITEM
					? WorkshopDepositMatchKind.SINGLE_EXACT
					: mode == ContainerLabelMode.ITEM_TAG ? WorkshopDepositMatchKind.SINGLE_ITEM_TAG
					: throwInvalidMode(mode),
				position, distanceSquared, scanIndex, mergeable
			);
		}

		private static WorkshopDepositMatchKind throwInvalidMode(ContainerLabelMode mode) {
			throw new IllegalArgumentException("A deposit target requires a concrete match kind: " + mode);
		}

		public Target {
			Objects.requireNonNull(matchKind, "matchKind");
			representativePosition = Objects.requireNonNull(representativePosition, "representativePosition").toImmutable();
			if (!Double.isFinite(distanceSquared) || distanceSquared < 0 || scanIndex < 0) {
				throw new IllegalArgumentException("Invalid workshop deposit target");
			}
		}
	}
}
