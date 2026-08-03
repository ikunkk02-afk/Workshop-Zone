package io.github.ikunkk02afk.workshopzone.network;

import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;
import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchResultCode;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkshopItemSearchPayloadTest {
	@Test
	void requestPayloadRoundTrips() {
		SearchWorkshopItemPayload original = new SearchWorkshopItemPayload(
			7, 12, 3, 9, Identifier.ofVanilla("iron_ingot")
		);
		RegistryByteBuf buffer = buffer();
		SearchWorkshopItemPayload.CODEC.encode(buffer, original);
		assertEquals(original, SearchWorkshopItemPayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void resultPayloadRoundTripsWithDoubleChestHighlights() {
		WorkshopItemSearchContainerResult container = new WorkshopItemSearchContainerResult(
			new BlockPos(1, 64, 1), List.of(new BlockPos(1, 64, 1), new BlockPos(2, 64, 1)),
			64L, 2, 9.5, true, 4
		);
		WorkshopItemSearchResultPayload original = new WorkshopItemSearchResultPayload(
			7, 12, 3, 9, WorkshopItemSearchResultCode.SUCCESS, Identifier.ofVanilla("potion"),
			64L, 1, false, List.of(container)
		);
		RegistryByteBuf buffer = buffer();
		WorkshopItemSearchResultPayload.CODEC.encode(buffer, original);
		WorkshopItemSearchResultPayload decoded = WorkshopItemSearchResultPayload.CODEC.decode(buffer);
		assertEquals(original.requestId(), decoded.requestId());
		assertEquals(original.sessionId(), decoded.sessionId());
		assertEquals(original.revision(), decoded.revision());
		assertEquals(original.syncId(), decoded.syncId());
		assertEquals(original.resultId(), decoded.resultId());
		assertEquals(original.targetItemId(), decoded.targetItemId());
		assertEquals(original.totalItemCount(), decoded.totalItemCount());
		assertEquals(original.totalMatchingContainers(), decoded.totalMatchingContainers());
		assertEquals(original.truncated(), decoded.truncated());
		assertEquals(container.representativePosition(), decoded.results().getFirst().representativePosition());
		assertEquals(container.highlightPositions(), decoded.results().getFirst().highlightPositions());
		assertEquals(container.containerItemCount(), decoded.results().getFirst().containerItemCount());
		assertEquals(container.matchingSlotCount(), decoded.results().getFirst().matchingSlotCount());
		assertEquals(container.distanceSquared(), decoded.results().getFirst().distanceSquared());
		assertEquals(container.multipleVariants(), decoded.results().getFirst().multipleVariants());
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void resultIdsAreStableUniqueAndUseRequestedPaths() {
		assertEquals(
			WorkshopItemSearchResultCode.values().length,
			Arrays.stream(WorkshopItemSearchResultCode.values()).map(WorkshopItemSearchResultCode::id).distinct().count()
		);
		assertEquals(Identifier.of("workshop_zone", "success"), WorkshopItemSearchResultCode.SUCCESS.id());
		for (WorkshopItemSearchResultCode result : WorkshopItemSearchResultCode.values()) {
			assertEquals(result, WorkshopItemSearchResultCode.fromId(result.id()).orElseThrow());
		}
	}

	@Test
	void moreThanSixtyFourResultsAreRejected() {
		List<WorkshopItemSearchContainerResult> results = java.util.stream.IntStream.range(0, 65)
			.mapToObj(index -> new WorkshopItemSearchContainerResult(
				new BlockPos(index, 64, 0), List.of(new BlockPos(index, 64, 0)), 1, 1, index, false, index
			)).toList();
		assertThrows(IllegalArgumentException.class, () -> new WorkshopItemSearchResultPayload(
			1, 2, 3, 4, WorkshopItemSearchResultCode.SUCCESS, Identifier.ofVanilla("iron_ingot"),
			65, 65, false, results
		));
	}

	private static RegistryByteBuf buffer() {
		return new RegistryByteBuf(Unpooled.buffer(), DynamicRegistryManager.EMPTY);
	}
}
