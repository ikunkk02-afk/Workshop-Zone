package io.github.ikunkk02afk.workshopzone.deposit;

import io.github.ikunkk02afk.workshopzone.label.ContainerLabelMode;
import io.github.ikunkk02afk.workshopzone.label.ContainerLabelSummary;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopDepositPlannerTest {
	@Test
	void exactLabelsSortBeforeItemTags() {
		WorkshopDepositPlanner.Target exact = target(ContainerLabelMode.EXACT_ITEM, new BlockPos(8, 64, 0), 64, 4, false);
		WorkshopDepositPlanner.Target tag = target(ContainerLabelMode.ITEM_TAG, new BlockPos(1, 64, 0), 1, 0, true);
		List<WorkshopDepositPlanner.Target> targets = new ArrayList<>(List.of(tag, exact));

		targets.sort(WorkshopDepositPlanner.TARGET_ORDER);

		assertEquals(List.of(exact, tag), targets);
	}

	@Test
	void mergeableThenDistanceThenScanOrderThenPositionAreStable() {
		WorkshopDepositPlanner.Target mergeable = target(ContainerLabelMode.EXACT_ITEM, new BlockPos(7, 64, 0), 49, 9, true);
		WorkshopDepositPlanner.Target near = target(ContainerLabelMode.EXACT_ITEM, new BlockPos(2, 64, 0), 4, 8, false);
		WorkshopDepositPlanner.Target scanFirst = target(ContainerLabelMode.EXACT_ITEM, new BlockPos(5, 64, 0), 25, 1, false);
		WorkshopDepositPlanner.Target scanSecond = target(ContainerLabelMode.EXACT_ITEM, new BlockPos(3, 64, 0), 25, 2, false);
		WorkshopDepositPlanner.Target positionFirst = target(ContainerLabelMode.EXACT_ITEM, new BlockPos(1, 64, 0), 36, 3, false);
		WorkshopDepositPlanner.Target positionSecond = target(ContainerLabelMode.EXACT_ITEM, new BlockPos(2, 64, 0), 36, 3, false);
		List<WorkshopDepositPlanner.Target> targets = new ArrayList<>(List.of(
			positionSecond, scanSecond, near, mergeable, positionFirst, scanFirst
		));

		targets.sort(WorkshopDepositPlanner.TARGET_ORDER);

		assertEquals(List.of(mergeable, near, scanFirst, scanSecond, positionFirst, positionSecond), targets);
	}

	@Test
	void unlabeledConflictingAndUnavailableSummariesAreNeverEligible() {
		ContainerLabelSummary exact = new ContainerLabelSummary(
			ContainerLabelMode.EXACT_ITEM, Optional.of(Identifier.ofVanilla("iron_ingot")), Optional.empty(),
			Optional.of(Identifier.ofVanilla("iron_ingot")), false, false
		);
		ContainerLabelSummary unavailable = new ContainerLabelSummary(
			ContainerLabelMode.ITEM_TAG, Optional.empty(), Optional.of(Identifier.ofVanilla("missing")),
			Optional.empty(), false, true
		);
		ContainerLabelSummary contentConflict = new ContainerLabelSummary(
			ContainerLabelMode.ITEM_TAG, Optional.empty(), Optional.of(Identifier.ofVanilla("logs")),
			Optional.of(Identifier.ofVanilla("oak_log")), true, false
		);

		assertTrue(WorkshopDepositPlanner.isEligible(exact));
		assertFalse(WorkshopDepositPlanner.isEligible(ContainerLabelSummary.NONE));
		assertFalse(WorkshopDepositPlanner.isEligible(ContainerLabelSummary.CONFLICT));
		assertFalse(WorkshopDepositPlanner.isEligible(unavailable));
		assertFalse(WorkshopDepositPlanner.isEligible(contentConflict));
	}

	private static WorkshopDepositPlanner.Target target(
		ContainerLabelMode mode, BlockPos position, double distanceSquared, int scanIndex, boolean mergeable
	) {
		return new WorkshopDepositPlanner.Target(mode, position, distanceSquared, scanIndex, mergeable);
	}
}
