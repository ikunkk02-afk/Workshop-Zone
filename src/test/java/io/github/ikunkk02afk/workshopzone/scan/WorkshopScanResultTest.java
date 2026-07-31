package io.github.ikunkk02afk.workshopzone.scan;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkshopScanResultTest {
	@Test
	void resultCopiesEntriesAndCalculatesCounts() {
		List<WorkshopBlockEntry> mutableEntries = new ArrayList<>();
		mutableEntries.add(WorkshopBlockEntry.create(
			WorkshopBlockType.CHEST,
			new BlockPos(1, 2, 3),
			Identifier.ofVanilla("chest"),
			4.0
		));
		mutableEntries.add(WorkshopBlockEntry.create(
			WorkshopBlockType.FURNACE,
			new BlockPos(2, 2, 3),
			Identifier.ofVanilla("furnace"),
			9.0
		));

		WorkshopScanResult result = WorkshopScanResult.create(BlockPos.ORIGIN, 8, 4, mutableEntries);
		mutableEntries.clear();

		assertEquals(2, result.size());
		assertEquals(1, result.containerCount());
		assertEquals(1, result.processingDeviceCount());
		assertThrows(UnsupportedOperationException.class, () -> result.entries().clear());
	}

	@Test
	void negativeRadiiAreRejected() {
		assertThrows(
			IllegalArgumentException.class,
			() -> WorkshopScanResult.create(BlockPos.ORIGIN, -1, 4, List.of())
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> WorkshopScanResult.create(BlockPos.ORIGIN, 8, -1, List.of())
		);
	}

	@Test
	void emptyResultIsReportedAsEmpty() {
		WorkshopScanResult result = WorkshopScanResult.create(BlockPos.ORIGIN, 8, 4, List.of());
		assertTrue(result.isEmpty());
	}
}
