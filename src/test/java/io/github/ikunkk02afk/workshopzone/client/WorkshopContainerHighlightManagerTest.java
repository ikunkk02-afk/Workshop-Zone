package io.github.ikunkk02afk.workshopzone.client;

import io.github.ikunkk02afk.workshopzone.search.WorkshopItemSearchContainerResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopContainerHighlightManagerTest {
	private static final Identifier OVERWORLD = Identifier.ofVanilla("overworld");
	private static final Identifier NETHER = Identifier.ofVanilla("the_nether");
	private static final Identifier IRON = Identifier.ofVanilla("iron_ingot");

	@Test
	void singleAndDoubleContainersProduceOneAndTwoPositions() {
		WorkshopContainerHighlightManager manager = new WorkshopContainerHighlightManager();
		manager.highlightOne(result(BlockPos.ORIGIN, List.of(BlockPos.ORIGIN)), IRON, OVERWORLD, 1000);
		assertEquals(1, manager.active(1000, OVERWORLD).getFirst().positions().size());
		BlockPos other = new BlockPos(1, 0, 0);
		manager.highlightOne(result(BlockPos.ORIGIN, List.of(BlockPos.ORIGIN, other)), IRON, OVERWORLD, 1000);
		assertEquals(2, manager.active(1000, OVERWORLD).getFirst().positions().size());
	}

	@Test
	void highlightsExpireClearOnDimensionChangeAndRepeatedLocateResetsTime() {
		WorkshopContainerHighlightManager manager = new WorkshopContainerHighlightManager();
		WorkshopItemSearchContainerResult result = result(BlockPos.ORIGIN, List.of(BlockPos.ORIGIN));
		manager.highlightOne(result, IRON, OVERWORLD, 1000);
		assertTrue(manager.active(5999, OVERWORLD).size() == 1);
		manager.highlightOne(result, IRON, OVERWORLD, 5000);
		assertTrue(manager.active(9999, OVERWORLD).size() == 1);
		assertTrue(manager.active(10000, OVERWORLD).isEmpty());
		manager.highlightOne(result, IRON, OVERWORLD, 11000);
		assertTrue(manager.active(11000, NETHER).isEmpty());
	}

	@Test
	void highlightAllCapsAtSixtyFourContainersAndOneHundredTwentyEightPositions() {
		WorkshopContainerHighlightManager manager = new WorkshopContainerHighlightManager();
		List<WorkshopItemSearchContainerResult> results = java.util.stream.IntStream.range(0, 80)
			.mapToObj(index -> {
				BlockPos first = new BlockPos(index * 2, 64, 0);
				return result(first, List.of(first, first.add(1, 0, 0)));
			}).toList();
		manager.highlightAll(results, IRON, OVERWORLD, 0);
		assertEquals(64, manager.active(0, OVERWORLD).size());
		assertEquals(128, manager.active(0, OVERWORLD).stream().mapToInt(value -> value.positions().size()).sum());
	}

	private static WorkshopItemSearchContainerResult result(BlockPos representative, List<BlockPos> highlights) {
		return new WorkshopItemSearchContainerResult(representative, highlights, 64, 1, 1, false, 0);
	}
}
