package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopItemSearchResultPayload;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchResultCode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopItemSearchResultFilterTest {
	@Test
	void onlyCurrentPendingRequestAndSessionIsAccepted() {
		WorkshopItemSearchResultPayload payload = payload(List.of(result(BlockPos.ORIGIN)));
		assertTrue(WorkshopItemSearchResultFilter.matches(payload, 7, 12, 3, 9));
		assertFalse(WorkshopItemSearchResultFilter.matches(payload, 8, 12, 3, 9));
		assertFalse(WorkshopItemSearchResultFilter.matches(payload, 7, 13, 3, 9));
		assertFalse(WorkshopItemSearchResultFilter.matches(payload, 7, 12, 4, 9));
		assertFalse(WorkshopItemSearchResultFilter.matches(payload, 7, 12, 3, 10));
	}

	@Test
	void resultsMissingFromCurrentSnapshotAreDiscarded() {
		BlockPos kept = new BlockPos(1, 64, 1);
		BlockPos stale = new BlockPos(2, 64, 2);
		List<WorkshopItemSearchContainerResult> filtered = WorkshopItemSearchResultFilter.filterExisting(
			payload(List.of(result(stale), result(kept))).results(), Set.of(kept)
		);
		assertEquals(List.of(result(kept)), filtered);
	}

	private static WorkshopItemSearchResultPayload payload(List<WorkshopItemSearchContainerResult> results) {
		long total = results.stream().mapToLong(WorkshopItemSearchContainerResult::containerItemCount).sum();
		return new WorkshopItemSearchResultPayload(
			7, 12, 3, 9, WorkshopItemSearchResultCode.SUCCESS, Identifier.ofVanilla("iron_ingot"),
			total, results.size(), false, results
		);
	}

	private static WorkshopItemSearchContainerResult result(BlockPos position) {
		return new WorkshopItemSearchContainerResult(position, List.of(position), 1, 1, 1, false, 0);
	}
}
