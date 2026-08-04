package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.network.WorkshopItemSearchResultPayload;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchResultCode;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopItemSearchResultFilterTest {
	@Test
	void onlyCurrentPendingRequestAndSessionIsAccepted() {
		WorkshopItemSearchResultPayload payload = payload(List.of(result(BlockPos.ORIGIN)));
		Identifier iron = Identifier.ofVanilla("iron_ingot");
		assertTrue(WorkshopItemSearchResultFilter.matches(payload, 7, 12, 3, 9, iron));
		assertFalse(WorkshopItemSearchResultFilter.matches(payload, 8, 12, 3, 9, iron));
		assertFalse(WorkshopItemSearchResultFilter.matches(payload, 7, 13, 3, 9, iron));
		assertFalse(WorkshopItemSearchResultFilter.matches(payload, 7, 12, 4, 9, iron));
		assertFalse(WorkshopItemSearchResultFilter.matches(payload, 7, 12, 3, 10, iron));
		assertFalse(WorkshopItemSearchResultFilter.matches(payload, 7, 12, 3, 9, Identifier.ofVanilla("gold_ingot")));
	}

	@Test
	void detailedResultsCarryContainerIdentityWithoutSnapshotMembership() {
		BlockPos position = new BlockPos(200, 64, 200);
		WorkshopItemSearchContainerResult result = new WorkshopItemSearchContainerResult(
			WorkshopBlockType.BARREL, Identifier.ofVanilla("barrel"), position, List.of(position),
			5, 1, 4.0, false, 129
		);

		assertEquals(WorkshopBlockType.BARREL, result.containerType());
		assertEquals(Identifier.ofVanilla("barrel"), result.blockId());
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
