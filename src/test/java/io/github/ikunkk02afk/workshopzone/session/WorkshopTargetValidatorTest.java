package io.github.ikunkk02afk.workshopzone.session;

import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockEntry;
import io.github.ikunkk02afk.workshopzone.scan.WorkshopBlockType;
import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkshopTargetValidatorTest {
	private static final BlockPos TARGET = new BlockPos(32, 64, 32);

	@Test
	void unloadedChunkIsRejectedWithoutReadingBlockState() {
		CountingAccess access = new CountingAccess();

		WorkshopTargetValidator.Result result = WorkshopTargetValidator.validate(access, chestEntry());

		assertEquals(WorkshopOpenResult.CHUNK_UNLOADED, result.result());
		assertEquals(0, access.blockStateReads);
	}

	private static WorkshopBlockEntry chestEntry() {
		return WorkshopBlockEntry.create(
			WorkshopBlockType.CHEST, TARGET, Identifier.ofVanilla("chest"), 1.0
		);
	}

	private static final class CountingAccess implements WorkshopTargetValidator.Access {
		private int blockStateReads;

		@Override
		public boolean isChunkLoaded(BlockPos position) {
			return false;
		}

		@Override
		public BlockState getBlockState(BlockPos position) {
			blockStateReads++;
			throw new AssertionError("Unloaded target must never read a block state");
		}
	}
}
