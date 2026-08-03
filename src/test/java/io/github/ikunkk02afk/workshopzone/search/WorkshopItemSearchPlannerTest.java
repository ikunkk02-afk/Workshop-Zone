package io.github.ikunkk02afk.workshopzone.search;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkshopItemSearchPlannerTest {
	@Test
	void resultsSortByDistanceThenQuantityThenScanOrderThenPosition() {
		WorkshopItemSearchContainerResult near = result(new BlockPos(5, 64, 0), 4.0, 1, 1);
		WorkshopItemSearchContainerResult more = result(new BlockPos(6, 64, 0), 9.0, 64, 4);
		WorkshopItemSearchContainerResult scanFirst = result(new BlockPos(8, 64, 0), 9.0, 32, 2);
		WorkshopItemSearchContainerResult scanSecond = result(new BlockPos(7, 64, 0), 9.0, 32, 3);
		List<WorkshopItemSearchContainerResult> values = new ArrayList<>(List.of(scanSecond, more, near, scanFirst));

		values.sort(WorkshopItemSearchPlanner.RESULT_ORDER);

		assertEquals(List.of(near, more, scanFirst, scanSecond), values);
	}

	private static WorkshopItemSearchContainerResult result(BlockPos position, double distance, long count, int scanIndex) {
		return new WorkshopItemSearchContainerResult(position, List.of(position), count, 1, distance, false, scanIndex);
	}
}
